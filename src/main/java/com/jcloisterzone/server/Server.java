package com.jcloisterzone.server;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcloisterzone.Application;
import com.jcloisterzone.Expansion;
import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Rotation;
import com.jcloisterzone.config.Config;
import com.jcloisterzone.figure.Follower;
import com.jcloisterzone.figure.Meeple;
import com.jcloisterzone.game.CustomRule;
import com.jcloisterzone.game.GameSettings;
import com.jcloisterzone.game.PlayerSlot;
import com.jcloisterzone.game.PlayerSlot.SlotState;
import com.jcloisterzone.game.PlayerSlot.SlotType;
import com.jcloisterzone.game.Snapshot;
import com.jcloisterzone.rmi.ClientIF;
import com.jcloisterzone.rmi.ServerIF;
import com.jcloisterzone.rmi.mina.ServerStub;


public class Server extends GameSettings implements ServerIF {

    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    //TODO
//	/** maximum length of player's nick, longer nicks are trimmed */
//	public static int MAX_NICK_LENGTH = 16;

    //private final Ini config;

    private boolean gameStarted;

    protected final PlayerSlot[] slots;
    protected EnumSet<Expansion>[] slotSupportedExpansions;
    protected int slotSerial;
    private Snapshot snapshot;

    /** server stub for sending and receiving messages */
    private ClientIF stub;

    private Random random = new Random();


    @SuppressWarnings("unchecked")
    public Server(Config config)  {
        slots = new PlayerSlot[PlayerSlot.COUNT];
        slotSupportedExpansions = new EnumSet[slots.length];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new PlayerSlot(i);
        }
        getExpansions().add(Expansion.BASIC);
        for (CustomRule cr : CustomRule.defaultEnabled()) {
            getCustomRules().add(cr);
        }
    }

    @SuppressWarnings("unchecked")
    public Server(Snapshot snapshot) {
        slots = snapshot.getPlayerSlots();
        slotSupportedExpansions = new EnumSet[slots.length]; //not used during load
        getExpansions().addAll(snapshot.getExpansions());
        getCustomRules().addAll(snapshot.getCustomRules());
        this.snapshot = snapshot;
    }

    public void engageSlots(long clientId) {
        for (PlayerSlot slot : slots) {
            if (slot != null && slot.getType() != SlotType.OPEN) {
                slot.setOwner(clientId);
            }
        }
    }

    public void start(int port) throws IOException {
        InvocationHandler handler = new ServerStub(this, port);
        stub = (ClientIF) Proxy.newProxyInstance(ClientIF.class.getClassLoader(), new Class[] { ClientIF.class }, handler);
    }

    public void stop() {
        ((ServerStub)Proxy.getInvocationHandler(stub)).stop();
    }

    public PlayerSlot[] getSlots() {
        return slots;
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    private EnumSet<Expansion> mergeSupportedExpansions() {
        EnumSet<Expansion> merged = null;
        for (int i = 0; i < slotSupportedExpansions.length; i++) {
            EnumSet<Expansion> supported = slotSupportedExpansions[i];
            if (supported == null) continue;
            if (merged == null) {
                merged = EnumSet.allOf(Expansion.class);
            }
            merged.retainAll(supported);
        }
        return merged;
    }

    @Override
    public void updateSlot(PlayerSlot slot, EnumSet<Expansion> supportedExpansions) {
        if (gameStarted) {
            logger.error(Application.ILLEGAL_STATE_MSG, "updateSlot");
            return;
        }
        if (snapshot == null) {
            //TODO check rights, maybe copy only
            if (slots[slot.getNumber()].getType() == SlotType.OPEN) { //old type
                slot.setSerial(++slotSerial);
            }
            if (slot.getType() == SlotType.OPEN) { //new type
                slot.setNick(null);
                slot.setSerial(null);
                slot.setState(null);
            } else {
                slot.setState(SlotState.ACTIVE);
            }
            if (slot.getType() != SlotType.AI) { //new type
                slot.setAiClassName(null);
            }
        }
        slots[slot.getNumber()] = slot;
        slotSupportedExpansions[slot.getNumber()] = supportedExpansions;
        stub.updateSlot(slot);
        //TODO
        stub.updateSupportedExpansions(mergeSupportedExpansions());
    }

    public void sessionStateChanged(Long clientId, SlotState state) {
        //temporaty disable
//        for (PlayerSlot slot : slots) {
//            if (slot != null && slot.getOwner() == clientId) {
//                slot.setState(state);
//                stub.updateSlot(slot);
//            }
//        }
    }

    @Override
    public void updateExpansion(Expansion expansion, Boolean enabled) {
        if (gameStarted) {
            logger.error(Application.ILLEGAL_STATE_MSG, "updateExpansion");
            return;
        }
        if (!expansion.isEnabled() || expansion == Expansion.BASIC) {
            logger.error("Invalid expansion {}", expansion);
            return;
        }
        if (enabled) {
            getExpansions().add(expansion);
        } else {
            getExpansions().remove(expansion);
        }
        //stub.updateGameSettings(slots, getExpansions(), getCustomRules());
        stub.updateExpansion(expansion, enabled);
    }

    @Override
    public void updateCustomRule(CustomRule rule, Boolean enabled) {
        if (gameStarted) {
            logger.error(Application.ILLEGAL_STATE_MSG, "updateCustomRule");
            return;
        }
        if (enabled) {
            getCustomRules().add(rule);
        } else {
            getCustomRules().remove(rule);
        }
        //stub.updateGameSettings(slots, getExpansions(), getCustomRules());
        stub.updateCustomRule(rule, enabled);
    }

    @Override
    public void updateGameSetup(Expansion[] expansions, CustomRule[] rules) {
        if (gameStarted) {
            logger.error(Application.ILLEGAL_STATE_MSG, "updateGameSetup");
            return;
        }
        getExpansions().clear();
        getExpansions().add(Expansion.BASIC);
        getExpansions().addAll(Arrays.asList(expansions));
        getCustomRules().clear();
        getCustomRules().addAll(Arrays.asList(rules));
        stub.updateGameSetup(expansions, rules);
    }

    @Override
    public void startGame() {
        ((ServerStub)Proxy.getInvocationHandler(stub)).closeAccepting();
        gameStarted = true;
        EnumSet<Expansion> supported = mergeSupportedExpansions();
        if (supported != null) {
            for (Expansion exp : Expansion.values()) {
                if (exp.isEnabled() && ! supported.contains(exp)) {
                    stub.updateExpansion(exp, false);
                }
            }
        }
        stub.startGame();
    }

    @Override
    public void pass() {
        stub.pass();
    }

    @Override
    public void placeTile(Rotation tileRotation, Position tilePosition) {
        stub.placeTile(tileRotation, tilePosition);
    }

    @Override
    public void deployMeeple(Position p, Location loc, Class<? extends Meeple> meepleType) {
        stub.deployMeeple(p, loc, meepleType);

    }

    @Override
    public void moveDragon(Position p) {
        stub.moveDragon(p);
    }

    @Override
    public void payRansom(Integer playerIndexToPay, Class<? extends Follower> meepleType) {
        stub.payRansom(playerIndexToPay, meepleType);

    }

    @Override
    public void selectTiles(int tilesCount, int drawCount) {
        assert tilesCount >= drawCount && drawCount > 0;
        int[] result = new int[drawCount];
        for (int i = 0; i < drawCount; i++) {
            result[i] = random.nextInt(tilesCount--);
        }
        stub.drawTiles(result);
    }

    @Override
    public void rollFlierDice(Class<? extends Meeple> meepleType) {
        stub.setFlierDistance(meepleType, 1+random.nextInt(3));
    }

    @Override
    public void moveFairy(Position p) {
        stub.moveFairy(p);
    }

    @Override
    public void placeTowerPiece(Position p) {
        stub.placeTowerPiece(p);
    }

    @Override
    public void placeTunnelPiece(Position p, Location d, boolean isSecondPiece) {
        stub.placeTunnelPiece(p, d, isSecondPiece);
    }

    @Override
    public void takePrisoner(Position p, Location d, Class<? extends Meeple> meepleType, Integer meepleOwner) {
        stub.takePrisoner(p, d, meepleType, meepleOwner);

    }

    @Override
    public void undeployMeeple(Position p, Location d, Class<? extends Meeple> meepleType, Integer meepleOwner) {
        stub.undeployMeeple(p, d, meepleType, meepleOwner);
    }

    @Override
    public void deployBridge(Position pos, Location loc) {
        stub.deployBridge(pos, loc);
    }

    @Override
    public void deployCastle(Position pos, Location loc) {
        stub.deployCastle(pos, loc);
    }

    @Override
    public void bazaarBid(Integer supplyIndex, Integer price) {
        stub.bazaarBid(supplyIndex, price);
    }

    @Override
    public void bazaarBuyOrSell(boolean buy) {
        stub.bazaarBuyOrSell(buy);
    }

    @Override
    public void cornCiclesRemoveOrDeploy(boolean remove) {
        stub.cornCiclesRemoveOrDeploy(remove);

    }

    @Override
    public void chatMessage(Integer author, String message) {
        stub.chatMessage(author, message);
    }

}
