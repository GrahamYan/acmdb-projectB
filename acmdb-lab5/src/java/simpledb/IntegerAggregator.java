package simpledb;
import java.lang.UnsupportedOperationException;
import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private final int gbfield;
    private final int afield;
    private final Op what;
    private final TupleDesc tupleDesc;

    private final Map<Field, Integer> groupCount, groupMin, groupMax, groupSum;
    private final Map<Field, Tuple> groupTuple;


    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.afield = afield;
        this.what = what;
        if (this.gbfield == NO_GROUPING) {
            this.tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE});
        } else {
            this.tupleDesc = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
        }
        this.groupTuple = new HashMap<>();
        switch (what) {
            case COUNT:
                this.groupCount = new HashMap<>();
                this.groupSum = null;
                this.groupMin = null;
                this.groupMax = null;
                break;
            case AVG:
                this.groupCount = new HashMap<>();
                this.groupSum = new HashMap<>();
                this.groupMin = null;
                this.groupMax = null;
                break;
            case SUM:
                this.groupCount = null;
                this.groupSum = new HashMap<>();
                this.groupMin = null;
                this.groupMax = null;
                break;
            case MAX:
                this.groupCount = null;
                this.groupSum = null;
                this.groupMin = null;
                this.groupMax = new HashMap<>();
                break;
            case MIN:
                this.groupCount = null;
                this.groupSum = null;
                this.groupMin = new HashMap<>();
                this.groupMax = null;
                break;
            default:
                this.groupCount = null;
                this.groupSum = null;
                this.groupMin = null;
                this.groupMax = null;
        }
    }

    private Integer init (Map<Field, Integer> map, Field key, Integer value) {
        Integer tmp = map.getOrDefault(key, null);
        if (tmp == null) {
            map.put(key, value);
            return value;
        }
        return tmp;
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field key = gbfield == NO_GROUPING ? null : tup.getField(gbfield);
        int value = ((IntField) tup.getField(afield)).getValue();
        int aggregrateValue = 0;
        switch (what) {
            case AVG:
                groupCount.put(key, init(groupCount, key, 0) + 1);
                groupSum.put(key, init(groupSum, key, 0) + value);
                aggregrateValue = groupSum.get(key) / groupCount.get(key);
                break;
            case COUNT:
                groupCount.put(key, init(groupCount, key, 0) + 1);
                aggregrateValue = groupCount.get(key);
                break;
            case SUM:
                groupSum.put(key, init(groupSum, key, 0) + value);
                aggregrateValue = groupSum.get(key);
                break;
            case MIN:
                groupMin.put(key, Math.min(value, init(groupMin, key, Integer.MAX_VALUE)));
                aggregrateValue = groupMin.get(key);
                break;
            case MAX:
                groupMax.put(key, Math.max(value, init(groupMax, key, Integer.MIN_VALUE)));
                aggregrateValue = groupMax.get(key);
                break;
        }
        Tuple newTuple = new Tuple(tupleDesc);
        if (key != null) {
            newTuple.setField(0, key);
            newTuple.setField(1, new IntField(aggregrateValue));
        } else {
            newTuple.setField(0, new IntField(aggregrateValue));
        }
        groupTuple.put(key, newTuple);
    }

    /**
     * Create a DbIterator over group aggregate results.
     * 
     * @return a DbIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public DbIterator iterator() {
        // some code goes here
        return new TupleIterator(tupleDesc, groupTuple.values());
    }

}
