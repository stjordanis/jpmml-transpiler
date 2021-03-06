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
package org.jpmml.transpiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.codemodel.JCodeModelClassLoader;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.HasPMML;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.Batch;
import org.jpmml.evaluator.testing.IntegrationTest;
import org.jpmml.evaluator.testing.IntegrationTestBatch;
import org.jpmml.model.PMMLUtil;

public class TranspilerTest extends IntegrationTest {

	private Visitor checker = null;


	public TranspilerTest(Equivalence<Object> equivalence){
		this(equivalence, new DefaultTranslationChecker());
	}

	public TranspilerTest(Equivalence<Object> equivalence, Visitor checker){
		super(equivalence);

		setChecker(checker);
	}

	@Override
	protected Batch createBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		Batch result = new IntegrationTestBatch(name, dataset, predicate, equivalence){

			@Override
			public TranspilerTest getIntegrationTest(){
				return TranspilerTest.this;
			}

			@Override
			public PMML getPMML() throws Exception {
				PMML xmlPmml = super.getPMML();

				JCodeModel codeModel = TranspilerUtil.translate(xmlPmml, null);

				TranspilerUtil.compile(codeModel);

				ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

				PMML javaPmml = PMMLUtil.load(clazzLoader);

				Visitor checker = getChecker();
				if(checker != null){
					checker.applyTo(javaPmml);
				}

				return javaPmml;
			}

			@Override
			protected void validateEvaluator(Evaluator evaluator) throws Exception {

				if(evaluator instanceof Serializable){
					HasPMML hasPMML = (HasPMML)evaluator;

					PMML pmml = hasPMML.getPMML();

					Class<? extends PMML> pmmlClazz = pmml.getClass();

					ClassLoader clazzLoader = (JCodeModelClassLoader)pmmlClazz.getClassLoader();

					clone(clazzLoader, (Serializable)evaluator);
				}
			}

			private Object clone(ClassLoader clazzLoader, Serializable object) throws Exception {
				byte[] buffer;

				ByteArrayOutputStream os = new ByteArrayOutputStream();

				ObjectOutputStream oos = new ObjectOutputStream(os);

				try {
					oos.writeObject(object);

					buffer = os.toByteArray();
				} finally {
					oos.close();
				}

				ByteArrayInputStream is = new ByteArrayInputStream(buffer);

				ObjectInputStream ois = new ObjectInputStream(is){

					@Override
					public Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws ClassNotFoundException, IOException {
						Class<?> clazz = Class.forName(objectStreamClass.getName(), false, clazzLoader);

						if(clazz != null){
							return clazz;
						}

						return super.resolveClass(objectStreamClass);
					}
				};

				try {
					return ois.readObject();
				} finally {
					ois.close();
				}
			}
		};

		return result;
	}

	public Visitor getChecker(){
		return this.checker;
	}

	public void setChecker(Visitor checker){
		this.checker = checker;
	}
}