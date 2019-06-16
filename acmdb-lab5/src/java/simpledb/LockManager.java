package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LockManager {
    private ConcurrentHashMap<PageId, CopyOnWriteArrayList<TransactionId>> sharedLocks;
    private ConcurrentHashMap<PageId, CopyOnWriteArrayList<TransactionId>> exclusiveLocks;
    private int num = 0;
    public LockManager() {
        sharedLocks = new ConcurrentHashMap<>();
        exclusiveLocks = new ConcurrentHashMap<>();
    }

    private synchronized void addLock(PageId pid, TransactionId tid, Permissions perm) {
        ++num;
        if (perm == Permissions.READ_ONLY) {
            if (sharedLocks.containsKey(pid)) {
                sharedLocks.get(pid).add(tid);
            } else {
                CopyOnWriteArrayList<TransactionId> ts = new CopyOnWriteArrayList<>();
                ts.add(tid);
                sharedLocks.put(pid, ts);
            }
        } else {
            if (exclusiveLocks.containsKey(pid)) {
                exclusiveLocks.get(pid).add(tid);
            } else {
                CopyOnWriteArrayList<TransactionId> ts = new CopyOnWriteArrayList<>();
                ts.add(tid);
                exclusiveLocks.put(pid, ts);
            }
        }
    }
    public synchronized boolean isYield(PageId pid, TransactionId tid, Permissions perm) {
        if (num < 100)
            return false;
        if (perm == Permissions.READ_ONLY) {
            if (exclusiveLocks.containsKey(pid)) {
                for (TransactionId t : exclusiveLocks.get(pid)) {
                    if (!t.equals(tid)) {
                        if (t.hashCode() < tid.hashCode()) {
                            return true;
                        }
                    }
                }
            }
        } else {
            if (exclusiveLocks.containsKey(pid)) {
                for (TransactionId t : exclusiveLocks.get(pid)) {
                    if (!t.equals(tid)) {
                        if (t.hashCode() < tid.hashCode()) {
                            return true;
                        }
                    }
                }
            }
            if (sharedLocks.containsKey(pid)) {
                for (TransactionId t : sharedLocks.get(pid)) {
                    if (!t.equals(tid)) {
                        if (t.hashCode() < tid.hashCode()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    public synchronized boolean grantLock(PageId pid, TransactionId tid, Permissions perm) {
        int ownS = 0, ownE = 0, otherS = 0, otherE = 0;
        if (sharedLocks.containsKey(pid)) {
            for (TransactionId t : sharedLocks.get(pid)) {
                if (t.equals(tid))
                    ownS++;
                else
                    otherS++;
            }
        }
        if (exclusiveLocks.containsKey(pid)) {
            for (TransactionId t : exclusiveLocks.get(pid)) {
                if (t.equals(tid))
                    ownE++;
                else
                    otherE++;
            }
        }
        if (perm == Permissions.READ_ONLY) {
            if (ownS == 1 && otherE == 0) {
                return true;
            } else
            if (ownS == 0 && otherE == 0) {
                addLock(pid, tid, perm);
                return true;
            } else
            if (otherE == 1) {
                return false;
            }
        } else {
            if (ownE == 1) {
                return true;
            } else
            if (ownE == 0 && otherE == 0 && otherS == 0) {
                addLock(pid,tid,perm);
                return true;
            } else
            if (otherE == 1 || otherS >= 1) {
                return false;
            }
        }
        return false;
    }

    public synchronized void releaseLock(PageId pid, TransactionId tid) {
        if (sharedLocks.containsKey(pid)) {
            sharedLocks.get(pid).remove(tid);
            if (sharedLocks.get(pid).isEmpty())
                sharedLocks.remove(pid);
        }
        if (exclusiveLocks.containsKey(pid)) {
            exclusiveLocks.get(pid).remove(tid);
            if (exclusiveLocks.get(pid).isEmpty())
                exclusiveLocks.remove(pid);
        }
    }

    public synchronized void releaseTransaction(TransactionId tid) {
        for (Map.Entry<PageId,CopyOnWriteArrayList<TransactionId>> e : sharedLocks.entrySet()) {
            if (e.getValue().contains(tid)) {
                releaseLock(e.getKey(),tid);
            }
        }
        for (Map.Entry<PageId,CopyOnWriteArrayList<TransactionId>> e : exclusiveLocks.entrySet()) {
            if (e.getValue().contains(tid)) {
                releaseLock(e.getKey(),tid);
            }
        }
    }
}