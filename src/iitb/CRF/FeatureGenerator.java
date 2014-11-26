package iitb.CRF;

import java.io.Serializable;

/**
 * The basic interface to be implemented by the user of this package
 * for providing features of an individual data sequence. 
 * 
 * @author Sunita Sarawagi
 * */

// this is the featureGenerator for some individual data sequence. that is, the sub- segment of the overall data.
// So it is actually the feature iterator/generator 
public interface FeatureGenerator extends Serializable {
    /** The number of features has to be correctly set before train is called. */
    int numFeatures();
    void startScanFeaturesAt(DataSequence data, int pos);
    boolean hasNext();
    Feature next();
    public String featureName(int featureIndex);
};
