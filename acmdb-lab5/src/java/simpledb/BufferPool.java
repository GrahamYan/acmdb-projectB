package simpledb;

import sun.security.pkcs11.wrapper.CK_LOCKMUTEX;

import javax.xml.crypto.Data;
import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * 
 * @Threadsafe, all fields are final
 */

class LockManager {
    enum LockType {
        SLock, XLock
    }
    class ObjLock {
        LockType type;
        PageId obj;
        ArrayList<TransactionId> holders;

        public ObjLock(LockType t, PageId obj, ArrayList<TransactionId> holders) {
            this.type = t;
            this.obj = obj;
            this.holders = holders;
        }

        public void setType(LockType type) {
            this.type = type;
        }

        public LockType getType() {
            return type;
        }

        public PageId getObj() {
            return obj;
        }

        public ArrayList<TransactionId> getHolders() {
            return holders;
        }

        public boolean tryUpgradeLock(TransactionId tid) {
            if (type == LockType.SLock && holders.size() == 1 && holders.get(0).equals(tid)) {
                type = LockType.XLock;
                return true;
            }
            return false;
        }

        public TransactionId addHolder(TransactionId tid) {
            if (type == LockType.SLock) {
                if (!holders.contains(tid)) {
                    holders.add(tid);
                }
                return tid;
            }
            return null;
        }
    }

    private ConcurrentHashMap<PageId, ObjLock> lockTable;
    private ConcurrentHashMap<TransactionId, ArrayList<PageId>> transactionTable;

    public LockManager(int lockTabCap, int transTabCap) {
        this.lockTable = new ConcurrentHashMap<PageId, LockManager.ObjLock>(lockTabCap);
        this.transactionTable = new ConcurrentHashMap<>(transTabCap);
    }

    public synchronized  boolean holdsLock(TransactionId tid, PageId pid) {
        ArrayList<PageId> lockList = getLockList(tid);
        return lockList != null && lockList.contains(pid);
    }

    private synchronized void block(PageId what, long start, long timeout) throws TransactionAbortedException {
        if (System.currentTimeMillis() - start > timeout) {
            throw new TransactionAbortedException();
        }
        try {
            wait(timeout);
            if (System.currentTimeMillis() - start > timeout) {
                throw new TransactionAbortedException();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized void updateTransactionTable(TransactionId tid, PageId pid) {
        if (transactionTable.containsKey(tid)) {
            if (!transactionTable.get(tid).contains(pid)) {
                transactionTable.get(tid).add(pid);
            }
        } else {
            ArrayList<PageId> lockList = new ArrayList<PageId>();
            lockList.add(pid);
            transactionTable.put(tid, lockList);
        }
    }

    public synchronized void acquireLock(TransactionId tid, PageId pid, LockType reqLock, int Timeout) throws TransactionAbortedException {
        long start = System.currentTimeMillis();
        Random rand = new Random();
        long randomTimeout = rand.nextInt(Timeout + 1);
        while (true) {
            if (lockTable.containsKey(pid)) {
                if (lockTable.get(pid).getType() == LockType.SLock) {
                    if (reqLock == LockType.SLock) {
                        updateTransactionTable(tid, pid);
                        assert lockTable.get(pid).addHolder(tid) != null;
                        return;
                    } else {
                        if (transactionTable.containsKey(tid) && transactionTable.get(tid).contains(pid) && lockTable.get(pid).getHolders().size() == 1) {
                            assert lockTable.get(pid).getHolders().get(0) == tid;
                            lockTable.get(pid).tryUpgradeLock(tid);
                            return;
                        } else {
                            block(pid, start, randomTimeout);
                        }
                    }
                } else {
                    if (lockTable.get(pid).getHolders().get(0) == tid) {
                        assert lockTable.get(pid).getHolders().size() == 1;
                        return;
                    } else {
                        block(pid, start, randomTimeout);
                    }
                }
            } else {
                ArrayList<TransactionId> initialHolders = new ArrayList<>();
                initialHolders.add(tid);
                lockTable.put(pid, new ObjLock(reqLock, pid, initialHolders));
                updateTransactionTable(tid, pid);
                return;
            }
        }
    }

    public synchronized void releaseLock(TransactionId tid, PageId pid) {
        if (transactionTable.containsKey(tid)) {
            transactionTable.get(tid).remove(pid);
            if (transactionTable.get(tid).size() == 0) {
                transactionTable.remove(tid);
            }
        }
        if (lockTable.containsKey(pid)) {
            lockTable.get(pid).getHolders().remove(tid);
            if (lockTable.get(pid).getHolders().size() == 0) {
                lockTable.remove(pid);
            } else {
                LockManager.this.notifyAll();
            }
        }
    }

    public synchronized void releaseLockOnTransaction(TransactionId tid) {
        if (transactionTable.containsKey(tid)) {
            PageId[] pidArray = new PageId[transactionTable.get(tid).size()];
            PageId[] toRelease = transactionTable.get(tid).toArray(pidArray);
            for (PageId pid : toRelease) {
                releaseLock(tid, pid);
            }
        }
    }

    public synchronized ArrayList<PageId> getLockList(TransactionId tid) {
        return transactionTable.getOrDefault(tid, null);
    }
}




public class BufferPool {
    /** Bytes per page, including header. */
    private static final int PAGE_SIZE = 4096;

    private static int pageSize = PAGE_SIZE;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    private int numPages;
    private ConcurrentHashMap<PageId, Page> bufferPool;

    private LockManager lockManager;
    private static int TRANSACTION_FACTOR = 2;
    private static int DEFAULT_MAXTIMEOUT = 5000;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.bufferPool = new ConcurrentHashMap<>(numPages);
        this.lockManager = new LockManager(numPages, TRANSACTION_FACTOR * numPages);
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, an page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        // some code goes here
        LockManager.LockType lockType;
        if (perm == Permissions.READ_ONLY) {
            lockType = LockManager.LockType.SLock;
        } else {
            lockType = LockManager.LockType.XLock;
        }
        //Debug.log(pid.toString() + ": before acquire lock\n");
        LockManager.acquireLock(tid, pid, lockType, 5000);
        //Debug.log(pid.toString() + ": acquired the lock\n");
        if (bufferPool.containsKey((pid))) {
            return bufferPool.get(pid);
        }
        else {
            if (bufferPool.size() < numPages) {
                Page tmp = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                bufferPool.put(pid, tmp);
                return tmp;
            }
            else {
                evictPage();
                Page tmp = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                bufferPool.put(pid, tmp);
                return tmp;
            }
        }
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        lockManager.releaseLock(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return lockManager.holdsLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        if (commit) {
            flushPages(tid);
        }
        ArrayList<PageId> lockList = lockManager.getLockList(tid);
        if (lockList != null) {
            for (PageId pid : lockList) {
                Page tmp = bufferPool.getOrDefault(pid, null);
                if (tmp != null && tmp.isDirty() != null) {
                    discardPage(pid);
                }
            }
        }
        lockManager.releaseLockOnTransaction(tid);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other 
     * pages that are updated (Lock acquisition is not needed for lab2). 
     * May block if the lock(s) cannot be acquired.
     * 
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        ArrayList<Page> pages = Database.getCatalog().getDatabaseFile(tableId).insertTuple(tid, t);
        for (Page tmp : pages) {
            tmp.markDirty(true, tid);
            bufferPool.remove(tmp.getId());
            bufferPool.put(tmp.getId(), tmp);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        ArrayList<Page> pages = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId()).deleteTuple(tid, t);
        for (Page tmp : pages) {
            tmp.markDirty(true, tid);
            bufferPool.remove(tmp.getId());
            bufferPool.put(tmp.getId(), tmp);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        Enumeration<PageId> tmp = bufferPool.keys();
        while (tmp.hasMoreElements()) {
            flushPage(tmp.nextElement());
        }
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
        
        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        bufferPool.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        if (bufferPool.contains(pid)) {
            Page tmp = bufferPool.get(pid);
            if (tmp.isDirty() != null) {
                DbFile dbf = Database.getCatalog().getDatabaseFile(tmp.getId().getTableId());
                dbf.writePage(tmp);
                tmp.markDirty(false, null);
            }
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        ArrayList<PageId> pagetoFlush = lockManager.getLockList(tid);
        if (pagetoFlush != null) {
            for (PageId p : pagetoFlush) {
                flushPage(p);
            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1
        Enumeration<PageId> tmp = bufferPool.keys();
        if (tmp.hasMoreElements()) {
            PageId pid = tmp.nextElement();
            try {
                flushPage(pid);
                discardPage(pid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
