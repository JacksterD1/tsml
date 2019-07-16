package classifiers.template.configuration.reduced;


import classifiers.distance_based.elastic_ensemble.iteration.AbstractIterator;
import classifiers.distance_based.elastic_ensemble.iteration.SiphonIterator;
import classifiers.distance_based.knn.sampling.DistributedRandomSampler;
import classifiers.distance_based.knn.sampling.LinearSampler;
import classifiers.distance_based.knn.sampling.RandomSampler;
import classifiers.distance_based.knn.sampling.RoundRobinRandomSampler;
import classifiers.template.configuration.TemplateConfig;
import utilities.ArrayUtilities;
import weka.core.Instance;
import weka.core.Instances;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class ReducedTrainSetConfig
    extends TemplateConfig {

    private final static String TRAIN_SET_SIZE_LIMIT_KEY = "trnsl";
    private final static String TRAIN_SET_SIZE_LIMIT_PERCENTAGE_KEY = "trnslp";
    private final static String TRAIN_ESTIMATE_SET_SIZE_LIMIT = "tressl";
    private final static String TRAIN_ESTIMATE_SET_SIZE_LIMIT_PERCENTAGE = "tresslp";
    private final static String TRAIN_SET_SIZE_THRESHOLD = "trnst"; // todo change keys
    private final static String TRAIN_ESTIMATE_SET_SIZE_THRESHOLD = "tresst";
    // iteration options
    private final static String TRAIN_SET_STRATEGY_KEY = "trnss";
    private final static String TRAIN_ESTIMATE_SET_SOURCE_KEY = "trsss";
    private final static String TRAIN_ESTIMATE_SET_STRATEGY_KEY = "tres";
    // sets
    private final static String PREDEFINED_TRAIN_SET_KEY = "pdtrs";
    private final static String PREDEFINED_TRAIN_ESTIMATE_SET_KEY = "pdtres";
    public static Comparator<ReducedTrainSetConfig> TRAIN_CONFIG_COMPARATOR = (config, other) -> { // todo
//            if(config.hasTrainEstimateSetSizeLimit() &&
//               config.trainEstimateSetSizeLimit < other.trainEstimateSetSizeLimit) return 1;
//            else if(config.hasTrainSetSizeLimit() &&
//                    config.trainSetSizeLimit < other.trainSetSizeLimit) return 1;
//            else if(!config.trainNeighbourSearchStrategy.equals(other.trainNeighbourSearchStrategy)) return 1;
//            else if(!config.trainEstimationSource.equals(other.trainEstimationSource)) return 1;
//            else if(!config.trainEstimationStrategy.equals(other.trainEstimationStrategy)) return 1;
//            else if(!config.predefinedTrainEstimateSet.equals(other.predefinedTrainEstimateSet)) return 1;
//            else if (!config.predefinedTrainSet.equals(other.predefinedTrainSet)) return 1;
//            else if(config.hasTrainSetSizeThreshold() &&
//                    config.trainSetSizeThreshold != other.trainSetSizeThreshold) return 1;
        return 0;
    };
    public static Comparator<ReducedTrainSetConfig> TEST_CONFIG_COMPARATOR = (config, other) -> { // todo
//            if(config.hasTestSetSizeLimit() && config.testSetSizeLimit < other.testSetSizeLimit) return 1;
        return 0;
    };
    // restriction options
    private final Random trainSetSamplingRandom = new Random();
    private final Random trainEstimateSetSamplingRandom = new Random();
    private int trainSetSizeLimit = -1;
    private double trainSetSizeLimitPercentage = -1;
    private int trainEstimateSetSizeLimit = -1;
    private double trainEstimateSetSizeLimitPercentage = -1;
    private int trainSetSizeThreshold = -1;
    private int trainEstimateSetSizeThreshold = -1;
    private NeighbourSearchStrategy trainSetStrategy = NeighbourSearchStrategy.RANDOM;
    private TrainEstimationSource trainEstimateSetSource = TrainEstimationSource.FROM_FULL_TRAIN_SET;
    private TrainEstimationStrategy trainEstimateSetStrategy = TrainEstimationStrategy.RANDOM;
    private boolean expectPredefinedTrainSet = false;
    private List<Instance> predefinedTrainSet = null; // todo keys
    private boolean expectPredefinedTrainEstimateSet = false;
    private List<Instance> predefinedTrainEstimateSet = null;
    // iterators for executing strategies
    private SiphonIterator<Instance> trainSetIterator = null;
    private AbstractIterator<Instance> trainEstimateSetIterator = null;

    public ReducedTrainSetConfig(final ReducedTrainSetConfig reducedTrainSetConfig) throws
                                                                                    Exception {
        super(reducedTrainSetConfig);
    }

    public ReducedTrainSetConfig() {}

    public boolean hasTrainEstimateSetSizeLimit() {
        return trainEstimateSetSizeLimit >= 0;
    }

    public boolean hasTrainSetSizeLimit() {
        return trainSetSizeLimit >= 0;
    }

    public void buildTrainIterators(Instances trainSet, Random random) {
        setupTrainEstimateSetSize(trainSet);
        setupTrainSetSize(trainSet);
        trainEstimateSetIterator = buildTrainEstimationStrategy(trainSet, random);
        trainSetIterator = buildTrainSetStrategy(trainSet, random, trainEstimateSetIterator);
    }

    public void setupTrainEstimateSetSize(Collection<Instance> trainSet) {
        if (trainEstimateSetSizeLimitPercentage >= 0) {
            if (trainEstimateSetSizeLimit >= 0) {
                throw new IllegalStateException("train set size set to both scalar and percentage");
            }
            trainEstimateSetSizeLimit = (int) (trainSet.size() * trainEstimateSetSizeLimitPercentage);
        }
    }

    public void setupTrainSetSize(Collection<Instance> trainSet) {
        if (trainSetSizeLimitPercentage >= 0) {
            if (trainSetSizeLimit >= 0) {
                throw new IllegalStateException("train set size set to both scalar and percentage");
            }
            trainSetSizeLimit = (int) (trainSet.size() * trainSetSizeLimitPercentage);
        }
    }

    private AbstractIterator<Instance> buildTrainEstimationStrategy(Collection<Instance> trainSet) {
        if (expectPredefinedTrainEstimateSet && predefinedTrainEstimateSet == null) {
            throw new IllegalStateException("predefined train estimate set has not been given but was expected!");
        }
        AbstractIterator<Instance> iterator;
        switch (getTrainEstimateSetStrategy()) {
            case RANDOM:
                iterator = new RandomSampler(trainEstimateSetSamplingRandom);
                break;
            case LINEAR:
                iterator = new LinearSampler();
                break;
            case ROUND_ROBIN_RANDOM:
                iterator = new RoundRobinRandomSampler(trainEstimateSetSamplingRandom);
                break;
            case DISTRIBUTED_RANDOM:
                iterator = new DistributedRandomSampler(trainEstimateSetSamplingRandom);
                break;
            default:
                throw new UnsupportedOperationException();
        }
        if (getPredefinedTrainEstimateSet() != null) {
            iterator.addAll(getPredefinedTrainEstimateSet());
        } else {
            switch (getTrainEstimateSetSource()) {
                case FROM_FULL_TRAIN_SET:
                    iterator.addAll(trainSet);
                    break;
                case FROM_REDUCED_TRAIN_SET:
                    // add the train neighbours as sampled from train set
                    break;
                default:
                    throw new IllegalStateException("train estimation source unknown");
            }
        }
        return iterator;
    }

    private SiphonIterator<Instance> buildTrainSetStrategy(Collection<Instance> trainSet,
                                                           final AbstractIterator<Instance> trainEstimateSetIterator) {
        if (expectPredefinedTrainSet && predefinedTrainSet == null) {
            throw new IllegalStateException("predefined train set has not been given but was expected!");
        }
        AbstractIterator<Instance> iterator;
        switch (getTrainSetStrategy()) { // todo factory?
            case RANDOM:
                iterator = new RandomSampler(trainSetSamplingRandom);
                break;
            case LINEAR:
                iterator = new LinearSampler();
                break;
            case ROUND_ROBIN_RANDOM:
                iterator = new RoundRobinRandomSampler(trainSetSamplingRandom);
                break;
            case DISTRIBUTED_RANDOM:
                iterator = new DistributedRandomSampler(trainSetSamplingRandom);
                break;
            default:
                throw new UnsupportedOperationException();
        }
        if (getPredefinedTrainSet() != null) {
            iterator.addAll(getPredefinedTrainSet());
        } else {
            iterator.addAll(trainSet);
        }
        SiphonIterator<Instance> siphonIterator = new SiphonIterator<>(iterator, null);
        if (getTrainEstimateSetSource().equals(TrainEstimationSource.FROM_REDUCED_TRAIN_SET)) {
            siphonIterator.setDestination(trainEstimateSetIterator);
        }
        return siphonIterator;
    }

    public TrainEstimationStrategy getTrainEstimateSetStrategy() {
        return trainEstimateSetStrategy;
    }

    public List<Instance> getPredefinedTrainEstimateSet() {
        return predefinedTrainEstimateSet;
    }

    public void setPredefinedTrainEstimateSet(final List<Instance> predefinedTrainEstimateSet) {
        this.predefinedTrainEstimateSet = predefinedTrainEstimateSet;
    }

    public TrainEstimationSource getTrainEstimateSetSource() {
        return trainEstimateSetSource;
    }

    public NeighbourSearchStrategy getTrainSetStrategy() {
        return trainSetStrategy;
    }

    public List<Instance> getPredefinedTrainSet() {
        return predefinedTrainSet;
    }

    public void setPredefinedTrainSet(final List<Instance> predefinedTrainSet) {
        this.predefinedTrainSet = predefinedTrainSet;
    }

    public void setTrainSetStrategy(final NeighbourSearchStrategy trainSetStrategy) {
        this.trainSetStrategy = trainSetStrategy;
    }

    public void setTrainEstimateSetSource(final TrainEstimationSource trainEstimateSetSource) {
        this.trainEstimateSetSource = trainEstimateSetSource;
    }

    public void setTrainEstimateSetStrategy(final TrainEstimationStrategy trainEstimateSetStrategy) {
        this.trainEstimateSetStrategy = trainEstimateSetStrategy;
    }

    public AbstractIterator<Instance> getTrainEstimateSetIterator() {
        return trainEstimateSetIterator;
    }

    public int getTrainSetSizeLimit() {
        return trainSetSizeLimit;
    }

    public void setTrainSetSizeLimit(final int trainSetSizeLimit) {
        this.trainSetSizeLimit = trainSetSizeLimit;
    }

    public double getTrainSetSizeLimitPercentage() {
        return trainSetSizeLimitPercentage;
    }

    public void setTrainSetSizeLimitPercentage(final double trainSetSizeLimitPercentage) {
        this.trainSetSizeLimitPercentage = trainSetSizeLimitPercentage;
    }

    public int getTrainEstimateSetSizeLimit() {
        return trainEstimateSetSizeLimit;
    }

    public void setTrainEstimateSetSizeLimit(final int trainEstimateSetSizeLimit) {
        this.trainEstimateSetSizeLimit = trainEstimateSetSizeLimit;
    }

    public double getTrainEstimateSetSizeLimitPercentage() {
        return trainEstimateSetSizeLimitPercentage;
    }

    public void setTrainEstimateSetSizeLimitPercentage(final double trainEstimateSetSizeLimitPercentage) {
        this.trainEstimateSetSizeLimitPercentage = trainEstimateSetSizeLimitPercentage;
    }

    public int getTrainSetSizeThreshold() {
        return trainSetSizeThreshold;
    }

    public void setTrainSetSizeThreshold(final int trainSetSizeThreshold) {
        this.trainSetSizeThreshold = trainSetSizeThreshold;
    }

    @Override
    public ReducedTrainSetConfig copy() throws
                                        Exception {
        return new ReducedTrainSetConfig(this);
    }

    @Override
    public void copyFrom(final Object object) throws
                                              Exception {
        ReducedTrainSetConfig other = (ReducedTrainSetConfig) object;
        setOptions(other.getOptions());
        predefinedTrainEstimateSet = other.predefinedTrainEstimateSet;
        predefinedTrainSet = other.predefinedTrainSet;
        if (other.trainEstimateSetIterator != null) {
            trainEstimateSetIterator = other.trainEstimateSetIterator.iterator();
        }
        if (other.trainSetIterator != null) {
            trainSetIterator = other.trainSetIterator.iterator();
            trainSetIterator.setDestination(trainEstimateSetIterator);
        }
    }

    @Override
    public void setOption(final String key, final String value) {
        switch (key) {
            case TRAIN_SET_SIZE_LIMIT_KEY:
                setTrainSetSizeLimit(Integer.parseInt(value));
                break;
            case TRAIN_SET_SIZE_LIMIT_PERCENTAGE_KEY:
                setTrainSetSizeLimitPercentage(Double.parseDouble(value));
                break;
            case TRAIN_ESTIMATE_SET_SIZE_LIMIT:
                setTrainEstimateSetSizeLimit(Integer.parseInt(value));
                break;
            case TRAIN_ESTIMATE_SET_SIZE_LIMIT_PERCENTAGE:
                setTrainEstimateSetSizeLimitPercentage(Double.parseDouble(value));
                break;
            case TRAIN_SET_STRATEGY_KEY:
                setTrainSetStrategy(NeighbourSearchStrategy.fromString(value));
                break;
            case TRAIN_ESTIMATE_SET_SOURCE_KEY:
                setTrainEstimateSetSource(TrainEstimationSource.fromString(value));
                break;
            case TRAIN_ESTIMATE_SET_STRATEGY_KEY:
                setTrainEstimateSetStrategy(TrainEstimationStrategy.fromString(value));
                break;
            case TRAIN_SET_SIZE_THRESHOLD:
                setTrainSetSizeThreshold(Integer.parseInt(value));
                break;
            case TRAIN_ESTIMATE_SET_SIZE_THRESHOLD:
                setTrainEstimateSetSizeThreshold(Integer.parseInt(value));
                break;
            case PREDEFINED_TRAIN_ESTIMATE_SET_KEY:
                expectPredefinedTrainEstimateSet = Boolean.parseBoolean(value);
            case PREDEFINED_TRAIN_SET_KEY:
                expectPredefinedTrainSet = Boolean.parseBoolean(value);
        }
    }

    @Override
    public String[] getOptions() {
        return ArrayUtilities.concat(super.getOptions(), new String[] {
            TRAIN_SET_SIZE_THRESHOLD,
            String.valueOf(trainSetSizeThreshold),
            TRAIN_SET_SIZE_LIMIT_KEY,
            String.valueOf(trainSetSizeLimit),
            TRAIN_SET_SIZE_LIMIT_PERCENTAGE_KEY,
            String.valueOf(trainSetSizeLimitPercentage),
            TRAIN_ESTIMATE_SET_SIZE_LIMIT,
            String.valueOf(trainEstimateSetSizeLimit),
            TRAIN_ESTIMATE_SET_SIZE_LIMIT_PERCENTAGE,
            String.valueOf(trainEstimateSetSizeLimitPercentage),
            TRAIN_SET_STRATEGY_KEY,
            String.valueOf(trainSetStrategy),
            TRAIN_ESTIMATE_SET_SOURCE_KEY,
            String.valueOf(trainEstimateSetSource),
            TRAIN_ESTIMATE_SET_STRATEGY_KEY,
            String.valueOf(trainEstimateSetStrategy),
            TRAIN_SET_SIZE_THRESHOLD,
            String.valueOf(trainSetSizeThreshold),
            TRAIN_ESTIMATE_SET_SIZE_THRESHOLD,
            String.valueOf(trainEstimateSetSizeThreshold),
            PREDEFINED_TRAIN_ESTIMATE_SET_KEY,
            String.valueOf(predefinedTrainEstimateSet),
            PREDEFINED_TRAIN_SET_KEY,
            String.valueOf(predefinedTrainSet)
        });
    }

    public AbstractIterator<Instance> getTrainSetIterator() {
        return trainSetIterator;
    }

    public int getTrainEstimateSetSizeThreshold() {
        return trainEstimateSetSizeThreshold;
    }

    public void setTrainEstimateSetSizeThreshold(final int trainEstimateSetSizeThreshold) {
        this.trainEstimateSetSizeThreshold = trainEstimateSetSizeThreshold;
    }
}
