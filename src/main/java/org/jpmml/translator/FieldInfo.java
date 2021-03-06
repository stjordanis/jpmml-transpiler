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
package org.jpmml.translator;

import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;

public class FieldInfo {

	private Field<?> field = null;

	private boolean primary = false;

	private Encoder encoder = null;

	private String variableName = null;


	public FieldInfo(Field<?> field){
		this(field, null);
	}

	public FieldInfo(Field<?> field, Encoder encoder){
		setField(field);
		setEncoder(encoder);
	}

	public Field<?> getField(){
		return this.field;
	}

	private void setField(Field<?> field){
		this.field = field;
	}

	public boolean isPrimary(){
		return this.primary;
	}

	public void setPrimary(boolean primary){
		this.primary = primary;
	}

	public Encoder getEncoder(){
		return this.encoder;
	}

	public void setEncoder(Encoder encoder){
		this.encoder = encoder;
	}

	public String getVariableName(){

		if(this.variableName == null){
			this.variableName = createVariableName();
		}

		return this.variableName;
	}

	public void setVariableName(String varibaleName){
		this.variableName = varibaleName;
	}

	private String createVariableName(){
		Field<?> field = getField();
		Encoder encoder = getEncoder();

		FieldName name = field.getName();

		String result = IdentifierUtil.sanitize(IdentifierUtil.truncate(name.getValue()));

		if(encoder != null){
			result = (result + "2" + encoder.getName());
		}

		return result;
	}
}