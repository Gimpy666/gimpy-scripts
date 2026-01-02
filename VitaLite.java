package com.tonic.plugins.ported;

import com.tonic.data.wrappers.TileItemEx;
import com.tonic.queries.TileItemQuery;
import com.tonic.util.VitaPlugin;
import com.tonic.Static;
import com.tonic.api.widgets.DialogueAPI;
import com.tonic.api.widgets.InventoryAPI;
import com.tonic.api.widgets.GrandExchangeAPI;
import com.tonic.api.game.QuestAPI;
import com.tonic.api.threaded.Delays;
import com.tonic.api.threaded.GrandExchange;
import com.tonic.api.threaded.Dialogues;
import com.tonic.services.pathfinder.Walker;
import com.tonic.queries.NpcQuery;
import com.tonic.queries.TileObjectQuery;
import com.tonic.data.wrappers.PlayerEx;
import com.tonic.data.wrappers.TileObjectEx;
import com.tonic.data.wrappers.NpcEx;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.ChatMessageType;
import javax.inject.Inject;

@PluginDescriptor(
    name = "Gertrude's Cat Automation",
    description = "Automates Gertrude's Cat Quest",
    tags = {"quest", "cat", "vitalite", "automation"}
)
public class GertrudesCatPlugin extends VitaPlugin {

    @Inject
    private Client client;

    // Items
    private static final int DOOGLE_LEAVES = ItemID.DOOGLELEAVES;
    private static final int RAW_SARDINE = ItemID.RAW_SARDINE;
    private static final int SEASONED_SARDINE = ItemID.SEASONED_SARDINE;
    private static final int BUCKET_OF_MILK = ItemID.BUCKET_MILK;
    private static final int KITTEN = ItemID.GERTRUDEKITTENS; 
    private static final int COINS = ItemID.COINS;

    // Locations
    private static final WorldPoint GERTRUDE_LOC = new WorldPoint(3148, 3413, 0);
    private static final WorldPoint DOOGLE_LOC = new WorldPoint(3156, 3404, 0); 
    private static final WorldPoint GE_LOC = new WorldPoint(3164, 3487, 0);
    private static final WorldPoint VARROCK_SQUARE = new WorldPoint(3222, 3435, 0);
    private static final WorldPoint LUMBERYARD_LADDER = new WorldPoint(3310, 3509, 0);
    private static final WorldPoint UPSTAIRS_CAT = new WorldPoint(3308, 3511, 1);

    @Override
    public void loop() throws Exception {
        if (!Static.invoke(() -> client.getGameState() == net.runelite.api.GameState.LOGGED_IN)) {
            return;
        }

        // Handle Dialogue first (Threaded)
        if (Static.invoke(DialogueAPI::dialoguePresent)) {
            // Priority to Quest Helper options if available
            if (Static.invoke(DialogueAPI::continueQuestHelper)) {
                Delays.tick();
                return;
            }
            // Otherwise continue normal dialogue
            Dialogues.continueAllDialogue(); 
            return; // Loop to re-check
        }

        int progress = Static.invoke(() -> client.getVarpValue(180));
        net.runelite.api.QuestState state = QuestAPI.getState(net.runelite.api.Quest.GERTRUDES_CAT);
        log("Quest Progress (Varp 180): " + progress + " | API State: " + state);

        switch (progress) {
            case 0: // Start Quest
                handleState0();
                break;
            case 1: // Talk to children
                handleState1();
                break;
            case 2: // Give Milk
                handleState2();
                break;
            case 3: // Give Sardine
                handleState3();
                break;
            case 4: // Find Kitten
                handleState4();
                break;
            case 5: // Finish
                handleState5();
                break;
            case 6: // Completed
                log("Quest Completed!");
                break;
            default:
                log("Unhandled Quest Progress: " + progress);
                break;
        }
    }

    private void handleState0() {
        if (checkDistance(GERTRUDE_LOC, 10)) {
            // Use Name for better robustness
             NpcEx gertrude = new NpcQuery().withName("Gertrude").nearest(); 
            if (gertrude != null) {
                log("Found Gertrude, interacting...");
                gertrude.interact("Talk-to");
                Delays.waitUntil(DialogueAPI::dialoguePresent, 5000);
            } else {
                log("Gertrude not found nearby.");
            }
        } else {
            log("Walking to Gertrude...");
            Walker.walkTo(GERTRUDE_LOC);
            Delays.waitUntil(() -> checkDistance(GERTRUDE_LOC, 5), 12000);
        }
    }

    private void handleState1() {
        // Requirements: Seasoned Sardine, Coins. 
        // Need Raw Sardine + Doogle Leaves to make Seasoned Sardine.
        
        if (!hasItem(SEASONED_SARDINE)) {
             if (!hasItem(RAW_SARDINE)) {
                 buyItem(RAW_SARDINE, 1, 500);
                 return;
             }
             
             if (!hasItem(DOOGLE_LEAVES)) {
                 log("Picking Doogle Leaves...1");
                 if (checkDistance(DOOGLE_LOC, 15)) {
                     TileItemEx leaves = new TileItemQuery().withName("Doogle leaves").nearest();
                     if (leaves != null) {
                         leaves.interact("Take");
                         Delays.waitUntil(() -> hasItem(DOOGLE_LEAVES), 3000);
                     }
                 } else {
                     Walker.walkTo(DOOGLE_LOC);
                     Delays.waitUntil(() -> checkDistance(DOOGLE_LOC, 5), 12000);
                 }
                 return;
             }

             log("Making Seasoned Sardine...");
             Static.invoke(() -> {
                 InventoryAPI.useOn(InventoryAPI.getItem(DOOGLE_LEAVES), InventoryAPI.getItem(RAW_SARDINE));
                 return true;
             });
             DialogueAPI.continueDialogue();
             Delays.waitUntil(() -> hasItem(SEASONED_SARDINE), 2000);
             return;
        }

        // Talk to Children
        if (checkDistance(VARROCK_SQUARE, 15)) {
            NpcEx child = new NpcQuery().withIds(NpcID.SHILOP, NpcID.WILOUGH).nearest();
            if (child != null) {
                child.interact("Talk-to");
                 Delays.waitUntil(DialogueAPI::dialoguePresent, 3000);
            }
        } else {
            Walker.walkTo(VARROCK_SQUARE);
             Delays.waitUntil(() -> checkDistance(VARROCK_SQUARE, 5), 12000);
        }
    }

    private void handleState2() {
        if (!hasItem(BUCKET_OF_MILK)) {
             buyItem(BUCKET_OF_MILK, 1, 200);
             return; 
        }

        if (isUpstairsLumberyard()) {
            Static.invoke(() -> {
                InventoryAPI.useOn(InventoryAPI.getItem(BUCKET_OF_MILK), new NpcQuery().withIds(NpcID.GERTRUDESCAT).nearest());
                return true;
            });
            Delays.waitUntil(() -> Static.invoke(() -> client.getVarpValue(180) > 2), 5000);
        } else {
            getToLumberyardUpstairs();
        }
    }

    private void handleState3() {
         if (!hasItem(SEASONED_SARDINE)) {
             log("Missing Seasoned Sardine!");
             return;
        }

        if (isUpstairsLumberyard()) {
            Static.invoke(() -> {
                InventoryAPI.useOn(InventoryAPI.getItem(SEASONED_SARDINE), new NpcQuery().withIds(NpcID.GERTRUDESCAT).nearest());
                return true;
            });
            Delays.waitUntil(() -> Static.invoke(() -> client.getVarpValue(180) > 3), 5000);
        } else {
             getToLumberyardUpstairs();
        }
    }

    private void handleState4() {
        if (hasItem(KITTEN)) {
            if (isUpstairsLumberyard()) {
                Static.invoke(() -> {
                    InventoryAPI.useOn(InventoryAPI.getItem(KITTEN), new NpcQuery().withIds(NpcID.GERTRUDESCAT).nearest());
                     return true;
                });
                Delays.waitUntil(() -> Static.invoke(() -> client.getVarpValue(180) > 4), 5000);
            } else {
               getToLumberyardUpstairs();
            }
        } else {
             if (isUpstairsLumberyard()) {
                 TileObjectEx ladder = new TileObjectQuery().withId(ObjectID.FAI_VARROCK_LADDERTOP).nearest();
                 if (ladder != null) {
                     ladder.interact("Climb-down");
                     Delays.waitUntil(() -> !isUpstairsLumberyard(), 3000);
                 }
             } else {
                 if (checkDistance(LUMBERYARD_LADDER, 15)) {
                     // Try NPC interact first (Kittens mew)
                     NpcEx mew = new NpcQuery().withIds(NpcID.KITTENS_MEW).nearest();
                     if (mew != null) {
                         mew.interact("Search");
                         Delays.wait(1500); 
                         return;
                     }
                     // Fallback to crate search? 
                     // Assuming QuestHelper logic mainly relies on the specific NPC/Object ID.
                 } else {
                     Walker.walkTo(LUMBERYARD_LADDER);
                     Delays.waitUntil(() -> checkDistance(LUMBERYARD_LADDER, 8), 12000);
                 }
             }
        }
    }

    private void handleState5() {
        if (checkDistance(GERTRUDE_LOC, 10)) {
             NpcEx gertrude = new NpcQuery().withName("Gertrude").nearest();
             if (gertrude != null) {
                 gertrude.interact("Talk-to");
                 Delays.waitUntil(DialogueAPI::dialoguePresent, 3000);
             } else {
                 log("Gertrude not found for state 5.");
             }
        } else {
            Walker.walkTo(GERTRUDE_LOC);
            Delays.waitUntil(() -> checkDistance(GERTRUDE_LOC, 5), 12000);
        }
    }

    private void buyItem(int id, int qty, int price) {
        if (Static.invoke(GrandExchangeAPI::isOpen)) {
            log("Buying item " + id + "...");
            GrandExchange.buy(id, qty, price, false);
        } else if (checkDistance(GE_LOC, 10)) {
            log("Opening GE...");
            NpcEx clerk = new NpcQuery().withName("Grand Exchange Clerk").nearest();
            if (clerk != null) {
                clerk.interact("Exchange");
            } else {
                 TileObjectEx booth = new TileObjectQuery().withName("Grand Exchange booth").nearest();
                 if (booth != null) booth.interact("Exchange");
            }
            Delays.waitUntil(() -> Static.invoke(GrandExchangeAPI::isOpen), 5000);
        } else {
            log("Walking to GE...");
            Walker.walkTo(GE_LOC);
            Delays.waitUntil(() -> checkDistance(GE_LOC, 5), 5000);
        }
    }

    // --- Helpers ---

    private boolean checkDistance(WorldPoint wp, int dist) {
        return Static.invoke(() -> PlayerEx.getLocal().getWorldPoint().distanceTo(wp) < dist);
    }
    
    private boolean hasItem(int id) {
        return Static.invoke(() -> InventoryAPI.getCount(id) > 0);
    }

    private boolean isUpstairsLumberyard() {
         return Static.invoke(() -> PlayerEx.getLocal().getWorldPoint().getPlane() == 1 
                                 && PlayerEx.getLocal().getWorldPoint().distanceTo(UPSTAIRS_CAT) < 20);
    }

    private void getToLumberyardUpstairs() {
        if (isUpstairsLumberyard()) return;
        
        if (checkDistance(LUMBERYARD_LADDER, 10)) {
            TileObjectEx ladder = new TileObjectQuery().withId(ObjectID.FAI_VARROCK_LADDER).nearest();
            if (ladder != null) {
                ladder.interact("Climb-up");
                Delays.waitUntil(this::isUpstairsLumberyard, 3000);
            }
        } else {
            Walker.walkTo(LUMBERYARD_LADDER);
            Delays.waitUntil(() -> checkDistance(LUMBERYARD_LADDER, 5), 12000);
        }
    }

    private void log(String msg) {
        Static.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null));
        System.out.println(msg);
    }
}