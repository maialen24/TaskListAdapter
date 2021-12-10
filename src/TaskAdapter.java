
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.interfaces.data.DataProviderException;
import com.lightstreamer.interfaces.data.FailureException;
import com.lightstreamer.interfaces.data.ItemEventListener;
import com.lightstreamer.interfaces.data.SmartDataProvider;
import com.lightstreamer.interfaces.data.SubscriptionException;


public class TaskAdapter implements SmartDataProvider {

    private static final String ITEM_NAME = "task";
    private int kont=0;
    private List<String> lista = new ArrayList<String>();

    /**
     * A static map, to be used by the Metadata Adapter to find the data
     * adapter instance; this allows the Metadata Adapter to forward client
     * messages to the adapter.
     * The map allows multiple instances of this Data Adapter to be included
     * in different Adapter Sets. Each instance is identified with the name
     * of the related Adapter Set; defining multiple instances in the same
     * Adapter Set is not allowed.
     */
    public static final ConcurrentHashMap<String, TaskAdapter> feedMap =
            new ConcurrentHashMap<String, TaskAdapter>();

    /**
     * Private logger; a specific "LS_demos_Logger.Chat" category
     * should be supplied by log4j configuration.
     */
    private Logger logger;

    /**
     * The listener of updates set by Lightstreamer Kernel.
     */
    private ItemEventListener listener;

    /**
     * Used to enqueue the calls to the listener.
     */
    private final ExecutorService executor;

    /**
     * An object representing the subscription.
     */
    private volatile Object subscribed;

    /**
     * Boolean flag for periodic flush of snapshot (call clearSnaphot).
     */
    private boolean flushSnapshot;

    /**
     * Interval period (in millis) for snapshot flush.
     */
    private int flushInterval;

    /**
     * Timer for snapshot flush.
     */
    private Timer myTimer;

    /**
     * Default interval period (in millis) for snapshot flush.
     */
    private static final int DEFAULT_FLUSH_INTERVAL = 30 * 60 * 1000;

    /**
     * Boolean flag that indicate messages presence.
     */
    private boolean messagesPresence;

    public TaskAdapter() {
        executor = Executors.newSingleThreadExecutor();

    }

    public void init(Map params, File configDir) throws DataProviderException {

        // Logging configuration for the demo is carried out in the init
        // method of Metadata Adapter. In order to be sure that this method
        // is executed after log configuration was completed, this parameter
        // must be present in the Adapter Set configuration (adapters.xml):
        // <metadata_adapter_initialised_first>Y</metadata_adapter_initialised_first>
        logger = LogManager.getLogger("LS_demos_Logger.Chat");

        // Read the Adapter Set name, which is supplied by the Server as a parameter
        String adapterSetId = (String) params.get("adapters_conf.id");


        if (params.containsKey("flush_snapshot")) {
            String tmp = (String) params.get("flush_snapshot");
            this.flushSnapshot = new Boolean(tmp).booleanValue();
        } else {
            this.flushSnapshot = true;
        }

        if (params.containsKey("flush_snapshot_interval")) {
            String tmp = (String) params.get("flush_snapshot_interval");
            this.flushInterval = new Integer(tmp).intValue();
        } else {
            this.flushInterval = DEFAULT_FLUSH_INTERVAL;
        }

        this.messagesPresence = false;

        // Put a reference to this instance on a static map
        // to be read by the Metadata Adapter
        feedMap.put(adapterSetId, this);

        // Adapter ready
        logger.info("TaskAdapter ready");

    }


    public void subscribe(String item, Object handle, boolean arg2)
            throws SubscriptionException, FailureException {

        if (!item.equals(ITEM_NAME)) {
            // only one item for a unique chat room is managed
            throw new SubscriptionException("No such item");
        }

        assert(subscribed == null);

        subscribed = handle;

        if(this.flushSnapshot) {
            // Start Thread for periodic flush of the snapshot.
            myTimer = new Timer(true);


            myTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    clearHistory();
                }
            }, new Date(System.currentTimeMillis() + this.flushInterval), this.flushInterval);
        }
    }


    public void unsubscribe(String arg0) throws SubscriptionException,
            FailureException {

        assert(subscribed != null);

        subscribed = null;

        if (myTimer != null) {
            myTimer.cancel();
            myTimer.purge();
            myTimer = null;
        }
    }

    public boolean isSnapshotAvailable(String arg0)
            throws SubscriptionException {
        //This adapter does not handle the snapshot.
        //If there is someone subscribed the snapshot is kept by the server
        return false;
    }


    public void setListener(ItemEventListener listener) {
        this.listener = listener;

    }




    public boolean sendMessage(String task) {
        logger.warn("sartu en send message");
        final Object currSubscribed = subscribed;
        if (currSubscribed == null) {
            return false;
        }

        //NB no anti-flood control
        String[] zeregina=task.split(":");
        String izena=zeregina[0];
        String egoera=zeregina[1];

        if (izena == null || izena.length() == 0) {
            logger.warn("Received empty or null izena");
            return false;
        }

        if (egoera == null || egoera.length() == 0) {
            logger.warn("Received empty or null langilea");
            return false;
        }

        this.messagesPresence = true;

        Date now = new Date();
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(now);
        long raw_timestamp = now.getTime();


        logger.debug(timestamp + "|New task: " + izena + "->" + egoera );

        final HashMap<String, String> update = new HashMap<String, String>();
        kont=kont+1;
        lista.add(task);

        update.put("izena", izena);
        update.put("egoera", egoera);
        update.put("kont", Integer.toString(kont));
        update.put("lista", lista.toString());
       // writeZeregina(izena,egoera);


        //If we have a listener create a new Runnable to be used as a task to pass the
        //new update to the listener
        Runnable updateTask = new Runnable() {
            public void run() {
                // call the update on the listener;
                // in case the listener has just been detached,
                // the listener should detect the case
                listener.smartUpdate(currSubscribed, update, false);

            }
        };

        //We add the task on the executor to pass to the listener the actual status
        executor.execute(updateTask);

        return true;
    }

    // used in case of flush_snapshot set to true.
    public void clearHistory() {
        final Object currSubscribed = subscribed;
        if (currSubscribed == null || this.messagesPresence == false) {
            return;
        }
        //If we have a listener create a new Runnable to be used as a task to pass the
        //event to the listener
        Runnable updateTask = new Runnable() {
            public void run() {
                // call the update on the listener;
                // in case the listener has just been detached,
                // the listener should detect the case
                listener.smartClearSnapshot(currSubscribed);
            }
        };

        executor.execute(updateTask);

        this.messagesPresence = false;
    }

    public void subscribe(String arg0, boolean arg1)
            throws SubscriptionException, FailureException {
        //NEVER CALLED

    }

}