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
package org.jpmml.translator.mining;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.InvalidElementException;
import org.jpmml.evaluator.MissingElementException;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.model.XPathUtil;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueFactoryRef;
import org.jpmml.translator.ValueMapBuilder;
import org.jpmml.translator.regression.RegressionModelTranslator;

public class ModelChainTranslator extends MiningModelTranslator {

	public ModelChainTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.getMiningFunction();
		switch(miningFunction){
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();

		Segmentation segmentation = miningModel.getSegmentation();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		switch(multipleModelMethod){
			case MODEL_CHAIN:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		List<Segment> segments = segmentation.getSegments();

		List<Segment> regressorSegments = segments.subList(0, segments.size() - 1);
		for(Segment regressorSegment : regressorSegments){
			Predicate predicate = regressorSegment.getPredicate();
			Model model = regressorSegment.getModel();

			if(!(predicate instanceof True)){
				throw new UnsupportedElementException(predicate);
			}

			MiningFunction modelMiningFunction = model.getMiningFunction();
			switch(modelMiningFunction){
				case REGRESSION:
					break;
				default:
					throw new UnsupportedAttributeException(model, modelMiningFunction);
			}

			MathContext modelMathContext = model.getMathContext();
			if(!Objects.equals(mathContext, modelMathContext)){
				throw new UnsupportedAttributeException(model, modelMathContext);
			}

			checkMiningSchema(model);

			Output modelOutput = model.getOutput();
			if(modelOutput == null){
				throw new MissingElementException(MissingElementException.formatMessage(XPathUtil.formatElement(model.getClass()) + "/" + XPathUtil.formatElement(Output.class)), model);
			} // End if

			if(modelOutput.hasOutputFields()){
				List<OutputField> outputFields = modelOutput.getOutputFields();

				if(outputFields.size() != 1){
					throw new UnsupportedElementException(modelOutput);
				}

				OutputField outputField = Iterables.getOnlyElement(outputFields);

				ResultFeature resultFeature = outputField.getResultFeature();
				switch(resultFeature){
					case PREDICTED_VALUE:
						break;
					default:
						throw new UnsupportedAttributeException(outputField, resultFeature);
				}
			} else

			{
				throw new MissingElementException(modelOutput, org.dmg.pmml.PMMLElements.OUTPUT_OUTPUTFIELDS);
			}

			@SuppressWarnings("unused")
			ModelTranslator<?> modelTranslator = newModelTranslator(model);
		}

		{
			Segment classifierSegment = segments.get(segments.size() - 1);

			Predicate predicate = classifierSegment.getPredicate();
			Model model = classifierSegment.getModel();

			if(!(predicate instanceof True)){
				throw new UnsupportedElementException(predicate);
			} // End if

			if(!(model instanceof RegressionModel)){
				throw new UnsupportedElementException(model);
			}

			MiningFunction modelMiningFunction = model.getMiningFunction();
			switch(modelMiningFunction){
				case CLASSIFICATION:
					break;
				default:
					throw new UnsupportedAttributeException(model, modelMiningFunction);
			}

			MathContext modelMathContext = model.getMathContext();
			if(!Objects.equals(mathContext, modelMathContext)){
				throw new UnsupportedAttributeException(model, modelMathContext);
			}

			checkMiningSchema(model);
			checkLocalTransformations(model);
			checkTargets(model);

			RegressionModel regressionModel = (RegressionModel)model;

			List<RegressionTable> regressionTables = regressionModel.getRegressionTables();
			for(RegressionTable regressionTable : regressionTables){

				if(regressionTable.hasNumericPredictors()){
					List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

					if(numericPredictors.size() > 1){
						throw new InvalidElementException(regressionTable);
					}
				} // End if

				if(regressionTable.hasCategoricalPredictors() || regressionTable.hasPredictorTerms()){
					throw new UnsupportedElementException(regressionTable);
				}
			}
		}
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.getSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Classification.class, segmentation, true, context);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			translateSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	private void translateSegmentation(Segmentation segmentation, TranslationContext context){
		MiningModel miningModel = getModel();

		List<Segment> segments = segmentation.getSegments();

		List<Segment> regressorSegments = segments.subList(0, segments.size() - 1);
		for(Segment regressorSegment : regressorSegments){
			Model model = regressorSegment.getModel();

			pullUpDerivedFields(miningModel, model);

			Output modelOutput = model.getOutput();

			OutputField outputField = Iterables.getOnlyElement(modelOutput.getOutputFields());

			ModelTranslator<?> modelTranslator = newModelTranslator(model);

			JMethod evaluateMethod = modelTranslator.translateRegressor(context);

			JInvocation methodInvocation = createEvaluatorMethodInvocation(evaluateMethod, context);

			context.declare(context.getValueType(), IdentifierUtil.create("value", outputField.getName()), methodInvocation);
		}

		ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
			.construct("values");

		{
			Segment classifierSegment = segments.get(segments.size() - 1);

			RegressionModel regressionModel = (RegressionModel)classifierSegment.getModel();

			List<RegressionTable> regressionTables = regressionModel.getRegressionTables();

			pullUpOutputFields(miningModel, regressionModel);

			for(RegressionTable regressionTable : regressionTables){
				List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

				Number intercept = regressionTable.getIntercept();

				JExpression valueExpr;

				NumericPredictor numericPredictor = Iterables.getFirst(numericPredictors, null);
				if(numericPredictor != null){
					valueExpr = context.getVariable(IdentifierUtil.create("value", numericPredictor.getField()));

					Number coefficient = numericPredictor.getCoefficient();
					if(coefficient != null && coefficient.doubleValue() != 1d){
						valueExpr = context.invoke(valueExpr, "multiply", coefficient);
					} // End if

					if(intercept != null && intercept.doubleValue() != 0d){
						valueExpr = context.invoke(valueExpr, "add", intercept);
					}
				} else

				{
					ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

					if(intercept != null && intercept.doubleValue() != 0d){
						valueExpr = valueFactoryRef.newValue(PMMLObjectUtil.createExpression(intercept, context));
					} else

					{
						valueExpr = valueFactoryRef.newValue();
					}
				}

				valueMapBuilder.update("put", regressionTable.getTargetCategory(), valueExpr);
			}

			RegressionModelTranslator.computeClassification(valueMapBuilder, regressionModel, context);
		}
	}
}