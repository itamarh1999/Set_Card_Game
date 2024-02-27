package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score=0;
    private BlockingQueue<Integer> tokens = new ArrayBlockingQueue<>(3);
    private Dealer dealer;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer=dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            while (tokens.size() == 3) {//in case of penalty for not a set
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException ignore) {}
                }
            }
            while (tokens.size() < 3) {//the main loop that waits for 3 tokens
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                }
            }
            dealer.addCheck(this.id);
            synchronized (this) {
                try {//dealer checking and we wait
                    dealer.DealerThread.interrupt();
                    wait();
                } catch (InterruptedException ignored) {}
            }
            synchronized (this) {
                try {
                    env.ui.setFreeze(id,milsToWait);
                    wait(milsToWait);
                } catch (InterruptedException ignored) {}
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        //note : no
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                while(tokens.size() <3) {
                    int rnd = (int) (Math.random() * ((double) env.config.tableSize));
                    this.keyPressed(rnd);
                }
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        this.terminate = true;
        // +++ TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) {
        for ( int i= 0; i < tokens.size(); i++) {
            if(tokens.contains(slot)) {
                    tokens.remove(slot);
                    table.removeToken(id, slot);
                    playerThread.interrupt();
                    return;
            }
        }
        if(tokens.remainingCapacity()>0) {
            tokens.add(slot);
            table.placeToken(id, slot);
            playerThread.interrupt();
        }
    }

    /**

     ** Award a point to a player and perform other related actions.
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        env.ui.setScore(this.id,score);
        playerThread.wait(env.config.pointFreezeMillis);

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        try {
            Thread.sleep(env.config.penaltyFreezeMillis);
        } catch (InterruptedException e) {
            env.logger.info("thread " + Thread.currentThread().getName() + " got an exception" +
                    ", Player::penalty() is bugged.");
        }
    }

    public int score() {
        return score;
    }


    public Queue<Integer> cardsTokens(){return tokens;}
    public void resetTokens(){tokens = new SynchronousQueue<>();}
}
