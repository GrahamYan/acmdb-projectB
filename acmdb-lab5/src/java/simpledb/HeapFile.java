package simpledb;

import java.io.*;
import java.util.*;

import static java.lang.System.in;
import static java.lang.System.out;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    private File f;
    private TupleDesc td;
    private int PagesNum;


    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.f = f;
        this.td = td;
        this.PagesNum = (int) Math.ceil(f.length()/ BufferPool.getPageSize());
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        //throw new UnsupportedOperationException("implement this");
        return f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        //throw new UnsupportedOperationException("implement this");
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        Page page = null;
        try (RandomAccessFile raf = new RandomAccessFile(getFile(), "r"))
        {
            byte[] buffer = new byte[BufferPool.getPageSize()];
            raf.seek(pid.pageNumber()*BufferPool.getPageSize());
            raf.read(buffer);
            page = new HeapPage((HeapPageId)pid, buffer);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return page;
    }



    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        PageId pid = page.getId();
        RandomAccessFile randomAccessFile = new RandomAccessFile(f, "rws");
        randomAccessFile.skipBytes(pid.pageNumber() * Database.getBufferPool().getPageSize());
        randomAccessFile.write(page.getPageData());
    }



    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int) Math.ceil(f.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        ArrayList<Page> pages = new ArrayList<>();
        for (int i = 0; i <= numPages(); i++)
        {
            HeapPageId hpid = new HeapPageId(getId(), i);
            HeapPage heapPage;
            if (i < numPages())
                heapPage = (HeapPage) Database.getBufferPool().getPage(tid, hpid, Permissions.READ_WRITE);
            else heapPage = new HeapPage(hpid, HeapPage.createEmptyPageData());
            if (heapPage.getNumEmptySlots() > 0)
            {
                heapPage.insertTuple(t);

                if (i < numPages())
                {
                    heapPage.markDirty(true,tid);
                    pages.add(heapPage);
                }
                //pages.add(heapPage);
                else writePage(heapPage);
                return pages;
            }

        }
        //if (pages.size() == 0)
        // {
        //     HeapPageId hpid = new HeapPageId(getId(), numPages());
        //     //HeapPage heapPage = (HeapPage) Database.getBufferPool().getPage(tid, hpid, Permissions.READ_WRITE);
        //     HeapPage newPage = new HeapPage(hpid, HeapPage.createEmptyPageData());
        //    newPage.insertTuple(t);
        //heapPage.markDirty(true,tid);
        //    pages.add(newPage);
        //    writePage(newPage);

        //}
        //return pages;
        throw new DbException("HeapFile: InsertTuple wrong");
        // not necessary for lab1
    }


    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        ArrayList<Page> pages = new ArrayList<>();
        RecordId rid = t.getRecordId();
        HeapPageId hpid= (HeapPageId)rid.getPageId();
        for (int i = 0; i < numPages(); i++)
        {
            if (i == hpid.pageNumber())
            {
                HeapPage heapPage = (HeapPage) Database.getBufferPool().getPage(tid, hpid, Permissions.READ_WRITE);
                heapPage.deleteTuple(t);
                heapPage.markDirty(true, tid);
                pages.add(heapPage);

            }
        }
        return pages;
        // not necessary for lab1
    }






    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(this, tid);
    }

    private class HeapFileIterator implements DbFileIterator
    {

        private TransactionId tid;
        private int ind;
        private Iterator<Tuple> TupleIterators;
        private HeapFile f;


        public HeapFileIterator(HeapFile f,TransactionId tid)
        {
            this.f = f;
            this.tid = tid;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException
        {
            if(TupleIterators == null)
                throw new NoSuchElementException("wrong");
            while (!TupleIterators.hasNext())
            {
                if (ind < f.numPages() - 1)
                {
                    ind++;
                    TupleIterators = ((HeapPage)Database.getBufferPool().getPage(tid, new HeapPageId(f.getId(), ind), Permissions.READ_ONLY)).iterator();
                }

            }
            //if (hasNext())
            //   return TupleIterators.next();
            return TupleIterators.next();
        }

        @Override
        public void close()
        {
            ind = 0;
            TupleIterators = null;
        }

        @Override
        public void open() throws DbException, TransactionAbortedException
        {
            this.ind = 0;
            if (numPages() == 0)
            {
                this.TupleIterators = new ArrayList<Tuple>().iterator();
            }
            else
            {
                HeapPageId tmp = new HeapPageId(getId(), ind);
                TupleIterators = ((HeapPage)Database.getBufferPool().getPage(tid, tmp, Permissions.READ_ONLY)).iterator();
            }
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException
        {
            if (TupleIterators == null)
                return false;
            if (TupleIterators.hasNext())
                return true;
            while (ind < numPages() - 1)
            {
                ind += 1;
                HeapPageId tmp = new HeapPageId(getId(), ind);
                TupleIterators = ((HeapPage) Database.getBufferPool().getPage(tid, tmp, Permissions.READ_ONLY)).iterator();
                return TupleIterators.hasNext();

            }
            return false;
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException
        {
            close();
            open();

        }
    }
}



