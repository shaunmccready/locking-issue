package com.example.locking;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public abstract class BaseLockService {

    /**
     * Initial amount of time to wait between retries.
     */
    private static final int BACKOFF_SLEEP_IN_MS = 1000;

    /**
     * Max number of times to retry.
     */
    private static final int BACKOFF_RETRY = 3;


    @Value("${zookeeper.hosts}")
    @Getter(AccessLevel.PROTECTED)
    private String zooKeeperHosts;

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    @Value("${lock.maxAge}")
    private long lockMaxAge;


    @Getter
    @Setter(AccessLevel.PACKAGE)
    private CuratorFramework client;

    /**
     * A Map of the locks that were acquired.
     */
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private Map<Integer, LockableHandle> locks;

    /**
     * Returns the lock path to use inside ZooKeeper to group the locks.
     *
     */
    protected abstract String getLockPath();

    /**
     * Creates an {@link InterProcessMutex} over ZooKeeper that acts as a lock. Note that the lock is not acquired
     * yet.
     *
     * @param lockPath The node path of the lock (mutex)
     * @return An {@link InterProcessMutex} that represent the lock.
     */
    protected InterProcessMutex createMutex(final String lockPath) {
        return new InterProcessMutex(client, lockPath);
    }


    protected CuratorFramework createClient() {
        return CuratorFrameworkFactory.newClient(zooKeeperHosts,
                new ExponentialBackoffRetry(BACKOFF_SLEEP_IN_MS, BACKOFF_RETRY));
    }

    /**
     * Initialize the service.
     */
    @PostConstruct
    public void init() {
        log.info("Stating initialization of the Lock Service");
        locks = new HashMap<>();
        client = createClient();
        client.start();

//        Debugging purposes
//        client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
//            @Override
//            public void stateChanged(CuratorFramework client, ConnectionState newState) {
//                System.out.println("** STATE CHANGED TO : " + newState);
//            }
//        });

        try {
            client.blockUntilConnected();
            if (client.isZk34CompatibilityMode()) {
                log.info("The Curator Framework is running in ZooKeeper 3.4 compatibility mode.");
            }
        } catch (InterruptedException ie) {
            log.error("Cannot connect to ZooKeeper.", ie);
        }

        log.info("Completed initialization of the Lock Service");
    }

    /**
     * Clean the acquired locks.
     */
    @PreDestroy
    public void destroy() {
        unlockAll();

        if (client != null) {
            client.close();
        }
    }

    /**
     * Unlocks all locks.
     */
    public void unlockAll() {
        unlockAll(Lists.newArrayList(locks.values()));
    }


    /**
     * Unlocks the given lock collection.
     *
     * @param lockHandles A List of {@link Lockable} to unlock.
     */
    public void unlockAll(final Collection<LockableHandle> lockHandles) {
        lockHandles.forEach(handle -> unlock(handle));
    }

    /**
     * Unlocks the lockable passed in parameter but only if it was already locked.
     * After the lock released, the node path will be deleted too, because InterProcessMutex will add an extra child
     * under the passed lockPath, when released, the child node will be removed, but node with passed lockpath will
     * not be removed. Using client.delete() to delete the lockPath.
     *
     * @param lockHandle The {@link Lockable} to lock.
     */
    public void unlock(final LockableHandle lockHandle) {
        boolean success = false;

        try {
            InterProcessMutex lock = lockHandle.getMutex();
            if (lock != null) {
                lock.release();
                client.delete()
                        .deletingChildrenIfNeeded()
                        .forPath(ZKPaths.makePath(getLockPath(), String.valueOf(lockHandle.getId())));
                success = true;
            }
        } catch (Exception e) {
            log.error("Can't unlock lock #" + lockHandle, e);
        } finally {
            locks.remove(lockHandle.getId());
        }

        log.info(String.format("The lock #%d was requested to be unlocked. Success = %b",
                lockHandle.getId(),
                success));
    }

    /**
     * Locks in ZooKeeper the {@link Lockable} list passed in parameters to make sure that no two instances
     * works on the same file. This lock in non-blocking, this means that if the {@link Lockable} is already
     * locked, the method will not include it in the result.
     *
     * @param desiredLock A List of {@link Lockable} that needs to be locked.
     * @param batchSize   The number of {@link Lockable} to lock.
     * @return A Set of {@link LockableHandle} that effectively got locked. Those that are not in the returned
     * list are either already locked or in error.
     */
    public Set<LockableHandle> lockBatch(final List<Lockable> desiredLock, final int batchSize) {
        Set<LockableHandle> effectivelyLocked = new HashSet<>();
        Iterator<Lockable> desiredLockIterator = desiredLock.iterator();

        while ((desiredLockIterator.hasNext()) && (effectivelyLocked.size() <= batchSize)) {
            Lockable toLock = desiredLockIterator.next();
            String lockPath = ZKPaths.makePath(getLockPath(), String.valueOf(toLock.getId()));
            InterProcessMutex lock = createMutex(lockPath);

            try {
                if (lock.acquire(0, TimeUnit.SECONDS)) {
                    LockableHandle handle = new LockableHandle(toLock, lock);
                    effectivelyLocked.add(handle);
                    locks.put(handle.getId(), handle);
                } else {
                    log.warn(String.format("Object was not locked. Object id is %d, lock path is %s.",
                            toLock.getId(),
                            lockPath));
                }
            } catch (Exception e) {
                log.error("Cannot lock path " + lockPath, e);
            }
        }

        log.info(String.format("%d object(s) were requested to lock. %d were effectively locked.",
                desiredLock.size(),
                effectivelyLocked.size()));

        return effectivelyLocked;
    }


    /**
     * A handle on the {@link InterProcessMutex} that locks the {@link Lockable} object.
     */
    public static class LockableHandle {
        @Getter(AccessLevel.PUBLIC)
        private Lockable lockedObject;

        @Getter(AccessLevel.PUBLIC)
        private InterProcessMutex mutex;

        @Getter(AccessLevel.PUBLIC)
        private Date creationDate;

        /**
         * Constructor.
         *
         * @param locked            The object that is locked.
         * @param interProcessMutex The mutex that represent the lock.
         */
        public LockableHandle(Lockable locked, InterProcessMutex interProcessMutex) {
            lockedObject = locked;
            mutex = interProcessMutex;
            creationDate = new Date();
        }

        public Integer getId() {
            return lockedObject.getId();
        }

    }
}
