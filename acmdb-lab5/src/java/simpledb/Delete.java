package simpledb;

import javax.xml.crypto.Data;
import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;
    private final TransactionId tid;
    private DbIterator child;
    private TupleDesc tupleDesc;
    private transient boolean fetched;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     *
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, DbIterator child) {
        // some code goes here
        this.tid = t;
        this.child = child;
        this.tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE});
        this.fetched = true;
    }

    public TupleDesc getTupleDesc() {
        // some code goes here
        return tupleDesc;
    }

    public void open() throws DbException, TransactionAbortedException {
        // some code goes here
        child.open();
        fetched = false;
        super.open();
    }

    public void close() {
        // some code goes here
        super.close();
        fetched = true;
        child.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        child.rewind();
        fetched = false;
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     *
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // some code goes here
        if (fetched) return null;
        fetched = true;
        int count = 0;
        for (; child.hasNext(); count++) {
            try {
                Database.getBufferPool().deleteTuple(tid, child.next());
            } catch (IOException e) {
                throw new DbException("Delete: IO error in dbFile delete tuple");
            }
        }
        Tuple tmp = new Tuple(tupleDesc);
        tmp.setField(0, new IntField(count));
        return tmp;
    }

    @Override
    public DbIterator[] getChildren() {
        // some code goes here
        return null;
    }

    @Override
    public void setChildren(DbIterator[] children) {
        // some code goes here
    }

}
