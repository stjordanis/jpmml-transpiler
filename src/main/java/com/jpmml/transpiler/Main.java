/*
 * Copyright (c) 2017 Villu Ruusmann
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
package com.jpmml.transpiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;
import org.jpmml.codemodel.ArchiverUtil;
import org.jpmml.model.PMMLUtil;

public class Main {

	@Parameter (
		names = {"--pmml-input"},
		description = "PMML input file",
		required = true
	)
	private File input = null;

	@Parameter (
		names = {"--jar-output"},
		description = "JAR output file",
		required = true
	)
	private File output = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			commander.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		main.run();
	}

	private void run() throws Exception {
		File input = getInput();
		File output = getOutput();

		PMML pmml;

		try(InputStream is = new FileInputStream(input)){
			pmml = PMMLUtil.unmarshal(is);
		}

		JCodeModel codeModel = TranspilerUtil.transpile(pmml);

		try(OutputStream os = new FileOutputStream(output)){
			ArchiverUtil.archive(codeModel, os);
		}
	}

	public File getInput(){
		return this.input;
	}

	public void setInput(File input){
		this.input = input;
	}

	public File getOutput(){
		return this.output;
	}

	public void setOutput(File output){
		this.output = output;
	}
}