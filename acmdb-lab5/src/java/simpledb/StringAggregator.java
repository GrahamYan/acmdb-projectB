package simpledb;
import java.util.*;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private final int gbfield;
    //private final Type gbfieldtype;
    private final int afield;
    //private final Op what;
    private final TupleDesc tupleDesc;

    private final Map<Field, Tuple> groupTuple;


    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.afield = afield;
        this.gbfield = gbfield;
        if (!Objects.equals(what, Op.COUNT)) {
            throw new IllegalArgumentException("StringAggregator only support COUNT");
        }
        if (this.gbfield == NO_GROUPING) {
            this.tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE});
        } else {
            this.tupleDesc = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
        }
        this.groupTuple = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field key = gbfield == NO_GROUPING ? null : tup.getField(gbfield);
        String value = ((StringField) tup.getField(afield)).getValue();
        Tuple old_Tuple = groupTuple.getOrDefault(key, null);
        Tuple new_Tuple = old_Tuple == null ? new Tuple(tupleDesc) : old_Tuple;
        int v;
        if (old_Tuple == null) v = 0;
        else {
            if (key == null) {
                v = ((IntField) old_Tuple.getField(0)).getValue();
            }
        else v = ((IntField) old_Tuple.getField(1)).getValue();
        }
        if (key == null) {
            new_Tuple.setField(0, new IntField(v + 1));
        } else {
            new_Tuple.setField(0, key);
            new_Tuple.setField(1, new IntField(v + 1));
        }
        groupTuple.put(key, new_Tuple);
    }

    /**
     * Create a DbIterator over group aggregate results.
     *
     * @return a DbIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public DbIterator iterator() {
        // some code goes here
        return new TupleIterator(tupleDesc, groupTuple.values());
    }

}
