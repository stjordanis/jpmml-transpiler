/*
 * Copyright (c) 2019 Villu Ruusmann
 *
 * This file is part of JPMML-Transpiler
 *
 * JPMML-Transpiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Transpiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Transpiler.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpmml.translator.mining;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.FieldInfo;
import com.jpmml.translator.IdentifierUtil;
import com.jpmml.translator.JVarBuilder;
import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.TranslationContext;
import com.jpmml.translator.ValueBuilder;
import com.jpmml.translator.ValueFactoryRef;
import com.jpmml.translator.tree.NodeScoreDistributionManager;
import com.jpmml.translator.tree.NodeScoreManager;
import com.jpmml.translator.tree.ScoreFunction;
import com.jpmml.translator.tree.TreeModelTranslator;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.ProbabilityAggregator;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueAggregator;
import org.jpmml.evaluator.ValueFactory;

public class TreeModelAggregatorTranslator extends MiningModelTranslator {

	public TreeModelAggregatorTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();
		switch(mathContext){
			case FLOAT:
			case DOUBLE:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, mathContext);
		}

		Segmentation segmentation = miningModel.getSegmentation();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		switch(multipleModelMethod){
			case SUM:
			case WEIGHTED_SUM:
			case AVERAGE:
			case WEIGHTED_AVERAGE:
			case MEDIAN:
			case WEIGHTED_MEDIAN:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		Segmentation.MissingPredictionTreatment missingPredictionTreatment = segmentation.getMissingPredictionTreatment();
		switch(missingPredictionTreatment){
			case RETURN_MISSING:
			case CONTINUE:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, missingPredictionTreatment);
		}

		List<Segment> segments = segmentation.getSegments();
		for(Segment segment : segments){
			Predicate predicate = segment.getPredicate();
			Model model = segment.getModel();

			if(!(predicate instanceof True)){
				throw new UnsupportedElementException(predicate);
			} // End if

			if(!(model instanceof TreeModel)){
				throw new UnsupportedElementException(model);
			}

			checkMiningSchema(model);
			checkLocalTransformations(model);
			checkTargets(model);
			checkOutput(model);

			@SuppressWarnings("unused")
			ModelTranslator<?> modelTranslator = newModelTranslator(model);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.getSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Value.class, segmentation, true, context);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			translateValueAggregatorSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.getSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Classification.class, segmentation, true, context);

		try {
			 context.pushScope(new MethodScope(evaluateMethod));

			 translateProbabilityAggregatorSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	@Override
	public Map<FieldName, FieldInfo> getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		Map<FieldName, FieldInfo> fieldInfos = super.getFieldInfos(bodyObjects);

		Set<Node> nodes = bodyObjects.stream()
			.map(bodyObject -> (Segmentation)bodyObject)
			.flatMap(segmentation -> (segmentation.getSegments()).stream())
			.map(segment -> (TreeModel)segment.getModel())
			.map(treeModel -> treeModel.getNode())
			.collect(Collectors.toCollection(LinkedHashSet::new));

		fieldInfos = TreeModelTranslator.enhanceFieldInfos(nodes, fieldInfos);

		return fieldInfos;
	}

	private void translateValueAggregatorSegmentation(Segmentation segmentation, TranslationContext context){
		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		List<Segment> segments = segmentation.getSegments();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(segmentation));

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		JVarBuilder aggregatorBuilder = new JVarBuilder(context);

		switch(multipleModelMethod){
			case SUM:
			case AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.UnivariateStatistic.class, "aggregator", valueFactoryRef);
				break;
			case MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.Median.class, "aggregator", valueFactoryRef, segments.size());
				break;
			case WEIGHTED_SUM:
			case WEIGHTED_AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.WeightedUnivariateStatistic.class, "aggregator", valueFactoryRef);
				break;
			case WEIGHTED_MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.WeightedMedian.class, "aggregator", valueFactoryRef, segments.size());
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		for(Segment segment : segments){
			True _true = (True)segment.getPredicate();
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			NodeScoreManager scoreManager = new NodeScoreManager(IdentifierUtil.create("scores", node), context);

			JInvocation nodeIndexExpr = createAndInvokeEvaluation(treeModel, node, scoreManager, fieldInfos, context);

			JExpression scoreExpr = scoreManager.getComponent(nodeIndexExpr);

			switch(multipleModelMethod){
				case SUM:
				case AVERAGE:
				case MEDIAN:
					aggregatorBuilder.update("add", scoreExpr);
					break;
				case WEIGHTED_SUM:
				case WEIGHTED_AVERAGE:
				case WEIGHTED_MEDIAN:
					aggregatorBuilder.update("add", scoreExpr, segment.getWeight());
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		}

		JVar aggregatorVar = aggregatorBuilder.getVariable();

		JInvocation valueInit;

		switch(multipleModelMethod){
			case SUM:
				valueInit = aggregatorVar.invoke("sum");
				break;
			case WEIGHTED_SUM:
				valueInit = aggregatorVar.invoke("weightedSum");
				break;
			case AVERAGE:
				valueInit = aggregatorVar.invoke("average");
				break;
			case WEIGHTED_AVERAGE:
				valueInit = aggregatorVar.invoke("weightedAverage");
				break;
			case MEDIAN:
				valueInit = aggregatorVar.invoke("median");
				break;
			case WEIGHTED_MEDIAN:
				valueInit = aggregatorVar.invoke("weightedMedian");
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		JVarBuilder resultBuilder = new ValueBuilder(context)
			.declare(Value.class, "result", valueInit);

		context._return(resultBuilder.getVariable());
	}

	private void translateProbabilityAggregatorSegmentation(Segmentation segmentation, TranslationContext context){
		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		List<Segment> segments = segmentation.getSegments();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(segmentation));

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		JVarBuilder aggregatorBuilder = new JVarBuilder(context);

		switch(multipleModelMethod){
			case AVERAGE:
				aggregatorBuilder.construct(ProbabilityAggregator.Average.class, "aggregator", valueFactoryRef);
				break;
			case WEIGHTED_AVERAGE:
				aggregatorBuilder.construct(ProbabilityAggregator.WeightedAverage.class, "aggregator", valueFactoryRef);
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		String[] categories = getTargetCategories();

		JFieldVar categoriesVar;

		{
			JDefinedClass owner = context.getOwner();

			JInvocation invocation = context.ref(Arrays.class).staticInvoke("asList");

			for(String category : categories){
				invocation.arg(JExpr.lit(category));
			}

			categoriesVar = owner.field(JMod.PRIVATE, List.class, "targetCategories", invocation);
		}

		aggregatorBuilder.update("init", categoriesVar);

		for(Segment segment : segments){
			True _true = (True)segment.getPredicate();
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			NodeScoreDistributionManager<?> scoreManager = new NodeScoreDistributionManager<Number>(IdentifierUtil.create("scores", node), categories, context){

				private ValueFactory<Number> valueFactory = ModelTranslator.getValueFactory(treeModel);


				@Override
				public ValueFactory<Number> getValueFactory(){
					return this.valueFactory;
				}
			};

			JInvocation nodeIndexExpr = createAndInvokeEvaluation(treeModel, node, scoreManager, fieldInfos, context);

			JExpression scoreExpr = scoreManager.getComponent(nodeIndexExpr);

			switch(multipleModelMethod){
				case AVERAGE:
					aggregatorBuilder.update("add", scoreExpr);
					break;
				case WEIGHTED_AVERAGE:
					aggregatorBuilder.update("add", scoreExpr, segment.getWeight());
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		}

		JVar aggregatorVar = aggregatorBuilder.getVariable();

		JInvocation valueMapInit;

		switch(multipleModelMethod){
			case AVERAGE:
				valueMapInit = aggregatorVar.invoke("averageMap");
				break;
			case WEIGHTED_AVERAGE:
				valueMapInit = aggregatorVar.invoke("weightedAverageMap");
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		context._return(JExpr._new(context.ref(ProbabilityDistribution.class)).arg(valueMapInit));
	}

	private <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> JInvocation createAndInvokeEvaluation(TreeModel treeModel, Node node, ScoreManager scoreManager, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		JMethod evaluateMethod = createEvaluatorMethod(int.class, node, false, context);

		JInvocation result = createEvaluatorMethodInvocation(evaluateMethod, context);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			TreeModelTranslator.translateNode(treeModel, node, scoreManager, fieldInfos, context);
		} finally {
			context.popScope();
		}

		return result;
	}
}