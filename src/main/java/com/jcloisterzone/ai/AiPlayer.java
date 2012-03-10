package com.jcloisterzone.ai;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcloisterzone.Player;
import com.jcloisterzone.UserInterface;
import com.jcloisterzone.action.CaptureAction;
import com.jcloisterzone.action.MeepleAction;
import com.jcloisterzone.action.PlayerAction;
import com.jcloisterzone.action.TilePlacementAction;
import com.jcloisterzone.board.Board;
import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Rotation;
import com.jcloisterzone.board.TilePack;
import com.jcloisterzone.feature.City;
import com.jcloisterzone.feature.Cloister;
import com.jcloisterzone.feature.Feature;
import com.jcloisterzone.feature.Road;
import com.jcloisterzone.game.Game;
import com.jcloisterzone.rmi.ServerIF;
import com.jcloisterzone.rmi.mina.ClientStub;

public abstract class AiPlayer implements UserInterface {
	
	protected final transient Logger logger = LoggerFactory.getLogger(getClass());

	private Game game;
	private ServerIF server;
	private Player player;

	public void setGame(Game game) {
		this.game = game;
	}

	public Game getGame() {
		return game;
	}

	public ServerIF getServer() {
		return server;
	}

	public void setServer(ServerIF server) {
		this.server = server;
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	protected Board getBoard() {
		return game.getBoard();
	}

	protected TilePack getTilePack() {
		return game.getTilePack();
	}

	protected ClientStub getClientStub() {
		return (ClientStub) Proxy.getInvocationHandler(server);
	}

	protected boolean isMe(Player p) {
		//nestaci porovnavat ref ?
		return p.getIndex() == player.getIndex();
	}

	public boolean isAiPlayerActive() {
		if (server == null) return false;
		Player activePlayer = game.getActivePlayer();
		if (activePlayer.getIndex() != player.getIndex()) return false;
		return getClientStub().isLocalPlayer(activePlayer);
	}
	
	@Override
	public void showWarning(String title, String message) {
		//do nothing
	}
	
	protected void handleRuntimeError(Exception e) {
		logger.error("AI player exception", e);
	}
	
	// dummy implementations
	
	protected final void selectDummyAbbeyPlacement(Set<Position> positions) {
		getServer().pass();
	}

	

	protected final void selectDummyAction(List<PlayerAction> actions, boolean canPass) {
		for(PlayerAction action : actions) {
			if (action instanceof TilePlacementAction) {
				if (selectDummyTilePlacement((TilePlacementAction) action)) return;
			}
			if (action instanceof MeepleAction) {				
				if (selectDummyMeepleAction((MeepleAction) action)) return;
			}
			if (action instanceof CaptureAction) {
				if (selectDummyTowerCapture((CaptureAction) action)) return;
			}
		}
		getServer().pass();
	}
	
	private boolean selectDummyTilePlacement(TilePlacementAction action) {
		Position nearest = null, p0 = new Position(0, 0);
		int min = Integer.MAX_VALUE;
		for(Position pos : action.getAvailablePlacements().keySet()) {
			int dist = pos.squareDistance(p0);
			if (dist < min) {
				min = dist;
				nearest = pos;
			}
		}
		getServer().placeTile(action.getAvailablePlacements().get(nearest).iterator().next(), nearest);
		return true;
	}
	
	private boolean selectDummyMeepleAction(MeepleAction ma) {
		Position p = ma.getSites().keySet().iterator().next();
		for(Location loc : ma.getSites().get(p)) {
			Feature f = getBoard().get(p).getFeature(loc);
			if (f instanceof City || f instanceof Road || f instanceof Cloister) {
				getServer().deployMeeple(p, loc, ma.getMeepleType());
				return true;
			}
		}
		return false;
	}

	private boolean selectDummyTowerCapture(CaptureAction action) {
		Position p = action.getSites().keySet().iterator().next();
		Location loc = action.getSites().get(p).iterator().next();
		getServer().captureFigure(p, loc);
		return true;
	}

	protected final void selectDummyDragonMove(Set<Position> positions, int movesLeft) {
		getServer().moveDragon(positions.iterator().next());
	}


}
