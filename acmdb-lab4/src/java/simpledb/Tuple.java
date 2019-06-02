package simpledb;

import java.io.Serializable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Tuple maintains information about the contents of a tuple. Tuples have a
 * specified schema specified by a TupleDesc object and contain Field objects
 * with the data for each field.
 */
public class Tuple implements Serializable {

    private static final long serialVersionUID = 1L;

    private ArrayList<Field> fields;
    private TupleDesc td;
    private RecordId rid;

    /**
     * Create a new tuple with the specified schema (type).
     *
     * @param td
     *            the schema of this tuple. It must be a valid TupleDesc
     *            instance with at least one field.
     */
    public Tuple(TupleDesc td) {
        // some code goes here
        this.td = td;
        this.fields = new ArrayList<Field>(Arrays.asList(new Field[td.numFields()]));

    }

    /**
     * @return The TupleDesc representing the schema of this tuple.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here

        return td;
    }

    /**
     * @return The RecordId representing the location of this tuple on disk. May
     *         be null.
     */
    public RecordId getRecordId() {
        // some code goes here
        return rid;
    }

    /**
     * Set the RecordId information for this tuple.
     *
     * @param rid
     *            the new RecordId for this tuple.
     */
    public void setRecordId(RecordId rid) {
        // some code goes here
        this.rid = rid;

    }

    /**
     * Change the value of the ith field of this tuple.
     *
     * @param i
     *            index of the field to change. It must be a valid index.
     * @param f
     *            new value for the field.
     */
    public void setField(int i, Field f) {
        // some code goes here
        fields.set(i, f);
    }

    /**
     * @return the value of the ith field, or null if it has not been set.
     *
     * @param i
     *            field index to return. Must be a valid index.
     */
    public Field getField(int i) {
        // some code goes here

        return fields.get(i);
    }

    /**
     * Returns the contents of this Tuple as a string. Note that to pass the
     * system tests, the format needs to be as follows:
     *
     * column1\tcolumn2\tcolumn3\t...\tcolumnN
     *
     * where \t is any whitespace (except a newline)
     */
    public String toString() {
        // some code goes here
        String column = new String();
        for (int i = 0; i < fields.size() - 1; ++i) {
            column += fields.get(i).toString() + '\t';
        }
        column += fields.get(fields.size() - 1);
        return column;
        //throw new UnsupportedOperationException("Implement this");
    }

    /**
     * @return
     *        An iterator which iterates over all the fields of this tuple
     * */
    public Iterator<Field> fields()
    {
        // some code goes here
        return fields.iterator();
    }

    /**
     * reset the TupleDesc of thi tuple
     * */
    public void resetTupleDesc(TupleDesc td)
    {
        // some code goes here
        this.td = td;
        this.fields = new ArrayList<Field>(Arrays.asList(new Field[td.numFields()]));
        for (int i = 0; i < td.numFields(); i++) {
            this.fields.add(new IntField(0));
        }
    }

    public static Tuple merge(Tuple a, Tuple b) {
        Tuple merged = new Tuple(TupleDesc.merge(a.getTupleDesc(), b.getTupleDesc()));

        int num1 = a.getTupleDesc().numFields();
        int num2 = b.getTupleDesc().numFields();
        for (int i = 0; i < num1; i++) {
            merged.setField(i, a.getField(i));
        }
        for (int i = 0; i < num2; i++) {
            merged.setField(i + num1, b.getField(i));
        }
        return merged;
    }
}
