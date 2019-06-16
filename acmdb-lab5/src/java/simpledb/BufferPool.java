package simpledb;

import java.io.*;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
public class BufferPool {
    /**
     * Bytes per page, including header.
     */
    private static final int PAGE_SIZE = 4096;

    private static int pageSize = PAGE_SIZE;
    private final LockManager lockManager = new LockManager();
    /**
     * Default number of pages passed to the constructor. This is used by
     * other classes. BufferPool should use the numPages argument to the
     * constructor instead.
     */
    public static final int DEFAULT_PAGES = 50;
    private int numPages = 0;
    private volatile ConcurrentHashMap<PageId, Page> pages;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        pages = new ConcurrentHashMap<>(numPages);
        this.numPages = numPages;
        // some code goes here
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
     * @param tid  the ID of the transaction requesting the page
     * @param pid  the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        boolean lock = lockManager.grantLock(pid,tid,perm);
        int num = 0;
        while (!lock) {
            ++num;
            if (num == 25 || lockManager.isYield(pid,tid,perm)) {
                throw new TransactionAbortedException();
            }
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                throw new TransactionAbortedException();
            }
            lock = lockManager.grantLock(pid,tid,perm);
        }
        try {
            if (pages.containsKey(pid)) {
                return pages.get(pid);
            }
            if (pages.size() < numPages) {
                Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pages.put(pid, page);
                return page;
            } else {
                Iterator<PageId> iter = pages.keySet().iterator();
                PageId evict = iter.next();
                while (pages.get(evict).isDirty() != null) {
                    if (!iter.hasNext())
                        throw new DbException("no enough clean page");
                    evict = iter.next();
                }
                flushPage(evict);
                pages.remove(evict);
                Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pages.put(pid, page);
                return page;
            }
        } catch (Exception e) {
            throw new DbException("Undefined Error");
        }
        // some code goes here
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
    public void releasePage(TransactionId tid, PageId pid) {
        lockManager.releaseLock(pid,tid);
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid,true);
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Return true if the specified transaction has a lock on the specified page
     */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return false;
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
            throws IOException {
        lockManager.releaseTransaction(tid);
        if (commit) {
            flushPages(tid);
        } else {
            ArrayList<Page> oldpages = new ArrayList<>();
            for (Page page : pages.values()) {
                if (page.isDirty() != null && page.isDirty().equals(tid)) {
                    PageId pid = page.getId();
                    Page old = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                    old.markDirty(false, null);
                    oldpages.add(old);
                }
            }
            for (Page page: oldpages) {
                pages.put(page.getId(),page);
            }
        }
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid     the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t       the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> pages = Database.getCatalog().getDatabaseFile(tableId).insertTuple(tid, t);
        for (Page page : pages){
            page.markDirty(true, tid);
            PageId pid = page.getId();
            if (this.pages.containsKey(pid)) {
                this.pages.put(pid,page);
                continue;
            }
            if (this.pages.size() < numPages) {
                this.pages.put(pid, page);
            } else {
                Iterator<PageId> iter = this.pages.keySet().iterator();
                PageId evict = iter.next();
                boolean mark = false;
                while (this.pages.get(evict).isDirty() != null) {
                    if (!iter.hasNext()) {
                        mark = true;
                        break;
                    }
                    evict = iter.next();
                }
                if (mark) {
                    flushPage(page.getId());
                    continue;
                }
                flushPage(evict);
                this.pages.remove(evict);
                this.pages.put(pid, page);
            }
        }
        // some code goes here
        // not necessary for lab1
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
    public void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        ArrayList<Page> pages = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId()).deleteTuple(tid, t);
        for (Page tmp : pages) {
            tmp.markDirty(true, tid);
            pages.remove(tmp.getId());
            this.pages.put(tmp.getId(), tmp);
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
        Enumeration<PageId> tmp = pages.keys();
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
        pages.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        if (pages.containsKey(pid)) {
            Page tmp = pages.get(pid);
            if (tmp.isDirty() != null) {
                DbFile dbf = Database.getCatalog().getDatabaseFile(tmp.getId().getTableId());
                dbf.writePage(tmp);
                tmp.markDirty(false, null);
            }
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException
    {
        if (pages.isEmpty())
            return;
        for (Page page : pages.values()) {
            if (page.isDirty() != null && page.isDirty().equals(tid)) {
                flushPage(page.getId());
            }
        }
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1

    }
}