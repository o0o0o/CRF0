package iitb.CRF;
/**
 * The basic interface to be implemented by the user of this package for
 * providing training and test data to the learner.
 *
 * @author Sunita Sarawagi
 *
 */ 
// I guess it is data iterator, to enumerate all the element. 
public interface DataIter {
    void startScan();
    boolean hasNext();
    DataSequence next();
};
