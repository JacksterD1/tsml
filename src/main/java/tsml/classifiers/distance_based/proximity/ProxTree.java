package tsml.classifiers.distance_based.proximity;

import com.beust.jcommander.internal.Lists;
import evaluation.storage.ClassifierResults;
import java.io.Serializable;
import java.util.List;
import java.util.ListIterator;

import org.junit.Assert;
import tsml.classifiers.distance_based.distances.DistanceMeasureConfigs;
import tsml.classifiers.distance_based.proximity.splitting.exemplar_based.ContinuousDistanceFunctionConfigs;
import tsml.classifiers.distance_based.proximity.splitting.exemplar_based.RandomExemplarPerClassPicker;
import tsml.classifiers.distance_based.proximity.splitting.exemplar_based.RandomExemplarSimilaritySplitter;
import tsml.classifiers.distance_based.proximity.splitting.Split;
import tsml.classifiers.distance_based.proximity.splitting.Splitter;
import tsml.classifiers.distance_based.utils.tree.BaseTree;
import tsml.classifiers.distance_based.utils.tree.BaseTreeNode;
import tsml.classifiers.distance_based.utils.tree.Tree;
import tsml.classifiers.distance_based.utils.tree.TreeNode;
import tsml.classifiers.distance_based.utils.classifier_building.CompileTimeClassifierBuilderFactory;
import tsml.classifiers.distance_based.utils.classifier_mixins.BaseClassifier;
import tsml.classifiers.distance_based.utils.iteration.LinearListIterator;
import tsml.classifiers.distance_based.utils.params.ParamSpace;
import utilities.ClassifierTools;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Purpose: // todo - docs - type the purpose of the code here
 * <p>
 * Contributors: goastler
 */
public class ProxTree extends BaseClassifier {

    public static final Factory FACTORY = new Factory();

    public static class Factory extends CompileTimeClassifierBuilderFactory<ProxTree> {
        public final ClassifierBuilder<? extends ProxTree> PROXIMITY_TREE =
            add(new SuppliedClassifierBuilder<>("PROXIMITY_TREE", Factory::buildProximityTree));

        public static ProxTree buildProximityTree() {
            ProxTree pt = new ProxTree();
            pt.setNodeIteratorBuilder(LinearListIterator::new);
            pt.setSplitterBuilder(new SplitterBuilder() {
                @Override
                public Splitter build() {
                    final Instances data = getData();
                    final ReadOnlyRandomSource randomSource = getRandomSource();
                    Assert.assertNotNull(data);
                    Assert.assertNotNull(randomSource);
                    final RandomExemplarPerClassPicker exemplarPicker = new RandomExemplarPerClassPicker(randomSource);
                    final List<ParamSpace> paramSpaces = Lists.newArrayList(
                        DistanceMeasureConfigs.buildEdSpace(),
                        DistanceMeasureConfigs.buildFullDtwSpace(),
                        DistanceMeasureConfigs.buildFullDdtwSpace(),
                        ContinuousDistanceFunctionConfigs.buildDtwSpace(data),
                        ContinuousDistanceFunctionConfigs.buildDdtwSpace(data),
                        ContinuousDistanceFunctionConfigs.buildErpSpace(data),
                        ContinuousDistanceFunctionConfigs.buildLcssSpace(data),
                        DistanceMeasureConfigs.buildMsmSpace(),
                        ContinuousDistanceFunctionConfigs.buildWdtwSpace(),
                        ContinuousDistanceFunctionConfigs.buildWddtwSpace(),
                        DistanceMeasureConfigs.buildTwedSpace()
                    );
                    return new RandomExemplarSimilaritySplitter(paramSpaces, randomSource, exemplarPicker);
                }
            });

            return pt;
        }
    }

    public static void main(String[] args) throws Exception {
        ProxTree pt = Factory.buildProximityTree();
        ClassifierResults results = ClassifierTools.trainAndTest("/bench/datasets", "GunPoint", 0, pt);
        System.out.println(results.writeSummaryResultsToString()); // todo interfaces or abst classes for fields in
        // this cls, e.g. node iterator?
    }

    public ProxTree() {
        try {
            shallowCopyFrom(Factory.buildProximityTree());
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Tree<Split> tree;
    private ListIterator<TreeNode<Split>> nodeIterator;
    private NodeIteratorBuilder nodeIteratorBuilder = (NodeIteratorBuilder) () -> {
        throw new UnsupportedOperationException();
    };
    private Splitter splitter;
    private SplitterBuilder splitterBuilder = new SplitterBuilder() {

        @Override
        public Splitter build() {
            throw new UnsupportedOperationException();
        }
    };
    private StoppingCondition stoppingCondition = (StoppingCondition) node -> {
        throw new UnsupportedOperationException();
    };

    public abstract static class SplitterBuilder implements Serializable {
        private Instances data;
        private ReadOnlyRandomSource randomSource;

        public SplitterBuilder setRandomSource(ReadOnlyRandomSource randomSource) {
            return null;
        }

        public SplitterBuilder setData(Instances data) {
            this.data = data;
            return this;
        }

        public abstract Splitter build();

        public Instances getData() {
            return data;
        }

        public ReadOnlyRandomSource getRandomSource() {
            return randomSource;
        }
    }

    public StoppingCondition getStoppingCondition() {
        return stoppingCondition;
    }

    public ProxTree setStoppingCondition(
        final StoppingCondition stoppingCondition) {
        this.stoppingCondition = stoppingCondition;
        return this;
    }

    public Tree<Split> getTree() {
        return tree;
    }

    public interface StoppingCondition extends Serializable {
        boolean shouldStop(TreeNode<Split> node);
        // todo some way to set this as the tree ref
    }

    public interface NodeIteratorBuilder extends Serializable {
        ListIterator<TreeNode<Split>> build();
    }

    @Override
    public void buildClassifier(Instances trainData) throws Exception {
        final boolean rebuild = isRebuild();
        super.buildClassifier(trainData);
        if(rebuild) {
            tree = new BaseTree<>();
            nodeIterator = nodeIteratorBuilder.build();
            final SplitterBuilder splitterBuilder = getSplitterBuilder();
            this.splitterBuilder.setData(trainData);
            this.splitterBuilder.setRandomSource(this);
            final Splitter splitter = this.splitterBuilder.build();
            setSplitter(splitter);
            final Split split = splitter.buildSplit(trainData);
            BaseTreeNode<Split> root = new BaseTreeNode<>(split);
            tree.setRoot(root);
            nodeIterator.add(root);
        }
        while(nodeIterator.hasNext()) {
            final TreeNode<Split> node = nodeIterator.next();
            final List<Instances> split = node.getElement().getPartitions();
            for(Instances childData : split) {
                final Split childSplit = splitter.buildSplit(childData);
                final TreeNode<Split> child = new BaseTreeNode<>(childSplit);
                final boolean shouldAdd = !stoppingCondition.shouldStop(node);
                if(shouldAdd) {
                    nodeIterator.add(child);
                }
            }
        }
    }

    @Override
    public double[] distributionForInstance(Instance instance) throws Exception {
        double[] distribution = new double[getNumClasses()];
        TreeNode<Split> node = tree.getRoot();
        int index = -1;
        while(!node.isLeaf()) {
            final Split split = node.getElement();
            index = split.getPartitionIndexOf(instance);
            final List<TreeNode<Split>> children = node.getChildren();
            node = children.get(index);
        }
        if(index < 0) {
            // todo log warning that we haven't done any tree traversal
            // todo perhaps rand pick result?
        } else {
            distribution[index]++;
        }
        return distribution;
    }

    public NodeIteratorBuilder getNodeIteratorBuilder() {
        return nodeIteratorBuilder;
    }

    public ProxTree setNodeIteratorBuilder(
        NodeIteratorBuilder nodeIteratorBuilder) {
        Assert.assertNotNull(nodeIteratorBuilder);
        this.nodeIteratorBuilder = nodeIteratorBuilder;
        return this;
    }

    public SplitterBuilder getSplitterBuilder() {
        return splitterBuilder;
    }

    public ProxTree setSplitterBuilder(SplitterBuilder splitterBuilder) {
        this.splitterBuilder = splitterBuilder;
        return this;
    }

    private Splitter getSplitter() {
        return splitter;
    }

    private ProxTree setSplitter(Splitter splitter) {
        this.splitter = splitter;
        return this;
    }
}
