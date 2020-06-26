package tsml.classifiers.distance_based.distances.transformed;
/*

Purpose: // todo - docs - type the purpose of the code here

Contributors: goastler
    
*/

import tsml.classifiers.distance_based.distances.DistanceMeasure;
import tsml.transformers.Transformer;
import weka.core.DistanceFunction;

public interface TransformDistanceMeasure extends DistanceMeasure {
    DistanceFunction getDistanceFunction();
    Transformer getTransformer();
    Transformer getAltTransformer();
    void setDistanceFunction(DistanceFunction distanceFunction);
    void setTransformer(Transformer transformer);
    void setAltTransformer(Transformer transformer);
    default boolean isSingleTransformer() {
        return getAltTransformer() == null;
    }
    default boolean isAltTransformer() {
        return !isSingleTransformer();
    }
}
