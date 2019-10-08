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
package com.jpmml.translator;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JVar;
import org.jpmml.evaluator.ValueFactory;

/**
 * @see ValueFactory
 */
public class ValueFactoryRef extends JVarRef {

	public ValueFactoryRef(JVar variable){
		super(variable);
	}

	/**
	 * @see ValueFactory#newValue()
	 */
	public JInvocation newValue(){
		return invoke("newValue");
	}

	/**
	 * @see ValueFactory#newValue(Number)
	 */
	public JInvocation newValue(JExpression valueExpr){
		return invoke("newValue", valueExpr);
	}
}