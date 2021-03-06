package simpledb;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class HashEquiJoin extends Operator {

    private static final long serialVersionUID = 1L;

    private final JoinPredicate p;
    private DbIterator child1;
    private DbIterator child2;

    private final transient Map<Field, List<Tuple>> map;
    private transient Tuple t2;
    private transient Iterator<Tuple> iterator;

    /**
     * Constructor. Accepts to children to join and the p to join them
     * on
     *
     * @param p
     *            The p to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     */
    public HashEquiJoin(JoinPredicate p, DbIterator child1, DbIterator child2) {
        // some code goes here
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
        this.map = new HashMap<>();
    }

    public JoinPredicate getJoinPredicate() {
        // some code goes here
        return p;
    }

    public TupleDesc getTupleDesc() {
        // some code goes here
        return TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
    }

    public String getJoinField1Name() {
        // some code goes here
        return child1.getTupleDesc().getFieldName(p.getField1());
    }

    public String getJoinField2Name() {
        // some code goes here
        return child2.getTupleDesc().getFieldName(p.getField2());
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        // some code goes here
        map.clear();
        child1.open();
        while (child1.hasNext()) {
            Tuple tmp = child1.next();
            Field field = tmp.getField(p.getField1());
            if (!map.containsKey(field)) {
                map.put(field, new ArrayList<>());
            }
            map.get(field).add(tmp);
        }
        child2.open();
        super.open();
    }

    public void close() {
        // some code goes here
        super.close();
        map.clear();
        child1.close();
        child2.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        child2.rewind();
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join p. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, there will be two copies of the join attribute in
     * the results. (Removing such duplicate columns can be done with an
     * additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     *
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // some code goes here
        while (true) {
            while (t2 == null) {
                if (!child2.hasNext()) {
                    return null;
                }
                t2 = child2.next();
                List<Tuple> tuples = map.getOrDefault(t2.getField(p.getField2()), null);
                if (tuples != null) {
                    iterator = tuples.iterator();
                } else {
                    t2 = null;
                }
            }
            if (iterator.hasNext()) {
                Tuple tmp = iterator.next();
                Tuple merge = new Tuple(TupleDesc.merge(tmp.getTupleDesc(), t2.getTupleDesc()));
                int num1 = tmp.getTupleDesc().numFields();
                int num2 = t2.getTupleDesc().numFields();
                for (int i = 0; i < num1; i++) {
                    merge.setField(i, tmp.getField(i));
                }
                for (int i = 0; i < num2; i++) {
                    merge.setField(i + num1, t2.getField(i));
                }
                return merge;
            } else {
                t2 = null;
            }
        }
    }

    @Override
    public DbIterator[] getChildren() {
        // some code goes here
        return new DbIterator[]{child1, child2};
    }

    @Override
    public void setChildren(DbIterator[] children) {
        // some code goes here
        this.child1 = children[0];
        this.child2 = children[1];
    }

}