/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.lops;

import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.sysml.hops.HopsException;
import org.apache.sysml.lops.LopProperties.ExecLocation;
import org.apache.sysml.lops.LopProperties.ExecType;
import org.apache.sysml.lops.compile.JobType;
import org.apache.sysml.parser.Expression.DataType;
import org.apache.sysml.parser.Expression.ValueType;
import org.apache.sysml.parser.ParameterizedBuiltinFunctionExpression;


/**
 * Defines a LOP for functions.
 * 
 */
public class ParameterizedBuiltin extends Lop 
{
	
	public enum OperationTypes { 
		INVALID, CDF, INVCDF, RMEMPTY, REPLACE, REXPAND, 
		PNORM, QNORM, PT, QT, PF, QF, PCHISQ, QCHISQ, PEXP, QEXP,
		TRANSFORM, TRANSFORMAPPLY, TRANSFORMDECODE, TRANSFORMMETA,
		TOSTRING
	};
	
	private OperationTypes _operation;
	private HashMap<String, Lop> _inputParams;
	private boolean _bRmEmptyBC;

	/**
	 * Creates a new builtin function LOP.
	 * 
	 * @param paramLops map of parameter lops
	 * @param op operation type
	 * @param dt data type
	 * @param vt value type
	 */
	public ParameterizedBuiltin(HashMap<String, Lop> paramLops, OperationTypes op, DataType dt, ValueType vt) 
	{
		super(Lop.Type.ParameterizedBuiltin, dt, vt);
		_operation = op;
		
		for (Lop lop : paramLops.values()) {
			this.addInput(lop);
			lop.addOutput(this);
		}
		
		_inputParams = paramLops;
		
		/*
		 * This lop is executed in control program. 
		 */
		boolean breaksAlignment = false;
		boolean aligner = false;
		boolean definesMRJob = false;
		lps.addCompatibility(JobType.INVALID);
		lps.setProperties(inputs, ExecType.CP, ExecLocation.ControlProgram, breaksAlignment, aligner, definesMRJob);
	}
	
	public ParameterizedBuiltin(HashMap<String, Lop> paramLops, OperationTypes op, DataType dt, ValueType vt, ExecType et) 
		throws HopsException 
	{
		super(Lop.Type.ParameterizedBuiltin, dt, vt);
		_operation = op;
		
		for (Lop lop : paramLops.values()) {
			this.addInput(lop);
			lop.addOutput(this);
		}
		
		_inputParams = paramLops;
		
		boolean breaksAlignment = false;
		boolean aligner = false;
		boolean definesMRJob = false;
		ExecLocation eloc = null;
		
		if( _operation == OperationTypes.REPLACE && et==ExecType.MR )
		{
			eloc = ExecLocation.MapOrReduce;
			lps.addCompatibility(JobType.GMR);
			lps.addCompatibility(JobType.DATAGEN);
			lps.addCompatibility(JobType.REBLOCK);
		}
		else if( _operation == OperationTypes.RMEMPTY && et==ExecType.MR )
		{
			eloc = ExecLocation.Reduce;
			lps.addCompatibility(JobType.GMR);
			lps.addCompatibility(JobType.DATAGEN);
			lps.addCompatibility(JobType.REBLOCK);
			breaksAlignment=true;
		}
		else if( _operation == OperationTypes.REXPAND && et==ExecType.MR )
		{
			eloc = ExecLocation.MapOrReduce;
			lps.addCompatibility(JobType.GMR);
			lps.addCompatibility(JobType.DATAGEN);
			lps.addCompatibility(JobType.REBLOCK);
			breaksAlignment=true;
		}
		else if ( _operation == OperationTypes.TRANSFORM && et == ExecType.MR ) {
			definesMRJob = true;
			eloc = ExecLocation.MapAndReduce;
			lps.addCompatibility(JobType.TRANSFORM);
		}
		else //executed in CP / CP_FILE / SPARK
		{
			eloc = ExecLocation.ControlProgram;
			lps.addCompatibility(JobType.INVALID);
		}
		lps.setProperties(inputs, et, eloc, breaksAlignment, aligner, definesMRJob);
	}

	public ParameterizedBuiltin(HashMap<String, Lop> paramLops, OperationTypes op, DataType dt, ValueType vt, ExecType et, boolean bRmEmptyBC) 
			throws HopsException 
	{
		this(paramLops, op, dt, vt, et);
		_bRmEmptyBC = bRmEmptyBC;
	}
	
	public OperationTypes getOp() { 
		return _operation; 
	}
	
	public int getInputIndex(String name) { 
		Lop n = _inputParams.get(name);
		for(int i=0; i<getInputs().size(); i++) 
			if(getInputs().get(i) == n)
				return i;
		return -1;
	}
	
	public Lop getNamedInput(String name) {
		return _inputParams.get(name);
	}
	
	@Override
	public String getInstructions(String output) 
		throws LopsException 
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );

		switch(_operation) 
		{
			case CDF:
			case INVCDF:
				sb.append( (_operation == OperationTypes.CDF ? "cdf" : "invcdf") );
				sb.append( OPERAND_DELIMITOR );
				
				for ( String s : _inputParams.keySet() ) 
				{	
					sb.append( s );
					sb.append( NAME_VALUE_SEPARATOR );
					
					// get the value/label of the scalar input associated with name "s"
					Lop iLop = _inputParams.get(s);
					sb.append( iLop.prepScalarLabel() );
					sb.append( OPERAND_DELIMITOR );
				}
				break;
				
			case RMEMPTY:
				sb.append("rmempty");
				sb.append(OPERAND_DELIMITOR);
				
				for ( String s : _inputParams.keySet() ) {
					
					sb.append(s);
					sb.append(NAME_VALUE_SEPARATOR);
					
					// get the value/label of the scalar input associated with name "s"
					// (offset and maxdim only apply to exec type spark)
					Lop iLop = _inputParams.get(s);
					if( s.equals( "target") || s.equals( "select") || getExecType()==ExecType.SPARK )
						sb.append( iLop.getOutputParameters().getLabel());
					else
						sb.append( iLop.prepScalarLabel() );
					
					sb.append(OPERAND_DELIMITOR);
				}
				
				break;
			
			case REPLACE: {
				sb.append( "replace" );
				sb.append( OPERAND_DELIMITOR );
				sb.append(compileGenericParamMap(_inputParams));
				break;
			}
			
			case REXPAND:
				sb.append("rexpand");
				sb.append(OPERAND_DELIMITOR);
				
				for ( String s : _inputParams.keySet() ) {
					
					sb.append(s);
					sb.append(NAME_VALUE_SEPARATOR);
					
					// get the value/label of the scalar input associated with name "s"
					// (offset and maxdim only apply to exec type spark)
					Lop iLop = _inputParams.get(s);
					if( s.equals( "target") || getExecType()==ExecType.SPARK )
						sb.append( iLop.getOutputParameters().getLabel());
					else
						sb.append( iLop.prepScalarLabel() );
					
					sb.append(OPERAND_DELIMITOR);
				}
				
				break;
				
			case TRANSFORM: 
			case TRANSFORMAPPLY:
			case TRANSFORMDECODE:
			case TRANSFORMMETA:	{
				sb.append(_operation.toString().toLowerCase()); //opcode
				sb.append(OPERAND_DELIMITOR);
				sb.append(compileGenericParamMap(_inputParams));
				break;
			}			
			case TOSTRING:{
				sb.append("toString"); //opcode
				sb.append(OPERAND_DELIMITOR);
				sb.append(compileGenericParamMap(_inputParams));
				break;
			}
				
			default:
				throw new LopsException(this.printErrorLocation() + "In ParameterizedBuiltin Lop, Unknown operation: " + _operation);
		}
		
		if (_operation == OperationTypes.RMEMPTY) {			
			sb.append("bRmEmptyBC");
			sb.append(NAME_VALUE_SEPARATOR);
			sb.append( _bRmEmptyBC );
			sb.append(OPERAND_DELIMITOR);
		}

		sb.append(this.prepOutputOperand(output));
		
		return sb.toString();
	}

	@Override 
	public String getInstructions(int input_index1, int input_index2, int input_index3, int output_index) 
		throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );

		switch(_operation) 
		{
			case REPLACE:
			{
				sb.append( "replace" );
				sb.append( OPERAND_DELIMITOR );
		
				Lop iLop = _inputParams.get("target");
				int pos = getInputs().indexOf(iLop);
				int index = (pos==0)? input_index1 : (pos==1)? input_index2 : input_index3;
				//input_index
				sb.append(prepInputOperand(index));
				sb.append( OPERAND_DELIMITOR );
				
				Lop iLop2 = _inputParams.get("pattern");
				sb.append(iLop2.prepScalarLabel());
				sb.append( OPERAND_DELIMITOR );
				
				Lop iLop3 = _inputParams.get("replacement");
				sb.append(iLop3.prepScalarLabel());
				sb.append( OPERAND_DELIMITOR );
				
				break;
			}	
				
			default:
				throw new LopsException(this.printErrorLocation() + "In ParameterizedBuiltin Lop, Unknown operation: " + _operation);
		}
		
		sb.append( prepOutputOperand(output_index));
		
		return sb.toString();
	}

	@Override 
	public String getInstructions(int input_index1, int input_index2, int input_index3, int input_index4, int output_index) 
		throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );

		switch(_operation) 
		{
			case RMEMPTY:
			{
				sb.append("rmempty");
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop1 = _inputParams.get("target");
				int pos1 = getInputs().indexOf(iLop1);
				int index1 = (pos1==0)? input_index1 : (pos1==1)? input_index2 : (pos1==2)? input_index3 : input_index4;
				sb.append(prepInputOperand(index1));
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop2 = _inputParams.get("offset");
				int pos2 = getInputs().indexOf(iLop2);
				int index2 = (pos2==0)? input_index1 : (pos2==1)? input_index2 : (pos1==2)? input_index3 : input_index4;
				sb.append(prepInputOperand(index2));
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop3 = _inputParams.get("maxdim");
				sb.append( iLop3.prepScalarLabel() );
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop4 = _inputParams.get("margin");
				sb.append( iLop4.prepScalarLabel() );
				
				sb.append( OPERAND_DELIMITOR );
				
				break;
			}
				
			default:
				throw new LopsException(this.printErrorLocation() + "In ParameterizedBuiltin Lop, Unknown operation: " + _operation);
		}
		
		sb.append( prepOutputOperand(output_index));
		
		return sb.toString();
	}
	
	@Override 
	public String getInstructions(int input_index1, int input_index2, int input_index3, int input_index4, int input_index5, int output_index) 
		throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );

		switch(_operation) 
		{
			case REXPAND:
			{
				sb.append("rexpand");
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop1 = _inputParams.get("target");
				int pos1 = getInputs().indexOf(iLop1);
				int index1 = (pos1==0)? input_index1 : (pos1==1)? input_index2 : (pos1==2)? input_index3 : (pos1==3)? input_index4 : input_index5;
				sb.append(prepInputOperand(index1));
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop2 = _inputParams.get("max");
				sb.append( iLop2.prepScalarLabel() );
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop3 = _inputParams.get("dir");
				sb.append( iLop3.prepScalarLabel() );
				
				sb.append(OPERAND_DELIMITOR);
				
				Lop iLop4 = _inputParams.get("cast");
				sb.append( iLop4.prepScalarLabel() );
				
				sb.append( OPERAND_DELIMITOR );
				
				Lop iLop5 = _inputParams.get("ignore");
				sb.append( iLop5.prepScalarLabel() );
				
				sb.append( OPERAND_DELIMITOR );
				
				break;
			}
				
			default:
				throw new LopsException(this.printErrorLocation() + "In ParameterizedBuiltin Lop, Unknown operation: " + _operation);
		}
		
		sb.append( prepOutputOperand(output_index));
		
		return sb.toString();
	}
	
	@Override 
	public String getInstructions(int output_index) 
		throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );

		if(_operation== OperationTypes.TRANSFORM) 
		{
			sb.append( "transform" );
			sb.append( OPERAND_DELIMITOR );
			
			Lop iLop = _inputParams.get(ParameterizedBuiltinFunctionExpression.TF_FN_PARAM_DATA);
			sb.append(iLop.prepInputOperand(getInputIndex("target")));
			sb.append( OPERAND_DELIMITOR );
			
			Lop iLop2 = _inputParams.get(ParameterizedBuiltinFunctionExpression.TF_FN_PARAM_MTD);
			sb.append(iLop2.prepScalarLabel());
			sb.append( OPERAND_DELIMITOR );
			
			// apply transform
			Lop iLop3a = _inputParams.get(ParameterizedBuiltinFunctionExpression.TF_FN_PARAM_APPLYMTD);
			if ( iLop3a != null ) {
				sb.append("applymtd=");
				sb.append(iLop3a.prepScalarLabel());
				sb.append( OPERAND_DELIMITOR );
			}
			
			// transform specification (transform: mandatory, transformapply: optional)
			Lop iLop3b = _inputParams.get(ParameterizedBuiltinFunctionExpression.TF_FN_PARAM_SPEC);
			if ( iLop3b != null ) {
				sb.append("spec=");
				sb.append(iLop3b.prepScalarLabel());
				sb.append( OPERAND_DELIMITOR );
			}
			
			Lop iLop4 = _inputParams.get(ParameterizedBuiltinFunctionExpression.TF_FN_PARAM_OUTNAMES);
			if( iLop4 != null ) {
				sb.append("outnames=");
				sb.append(iLop4.prepScalarLabel());
				sb.append( OPERAND_DELIMITOR );
			}
			
			sb.append( prepOutputOperand(output_index));
		}
		else
			throw new LopsException(this.printErrorLocation() + "In ParameterizedBuiltin Lop, Unknown operation: " + _operation);
	
		return sb.toString();
	}
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(_operation.toString());

		if( !getInputs().isEmpty() )
			sb.append("(");
		for (Lop cur : getInputs()) {
			sb.append(cur.toString());
		}
		if( !getInputs().isEmpty() )
			sb.append(") ");

		sb.append(" ; num_rows=" + this.getOutputParameters().getNumRows());
		sb.append(" ; num_cols=" + this.getOutputParameters().getNumCols());
		sb.append(" ; format=" + this.getOutputParameters().getFormat());
		sb.append(" ; blocked=" + this.getOutputParameters().isBlocked());
		return sb.toString();
	}
	
	private static String compileGenericParamMap(HashMap<String, Lop> params) {
		StringBuilder sb = new StringBuilder();		
		for ( Entry<String, Lop> e : params.entrySet() ) {
			sb.append(e.getKey());
			sb.append(NAME_VALUE_SEPARATOR);
			if( e.getValue().getDataType() != DataType.SCALAR )
				sb.append( e.getValue().getOutputParameters().getLabel());
			else
				sb.append( e.getValue().prepScalarLabel() );
			sb.append(OPERAND_DELIMITOR);
		}
		
		return sb.toString();
	}
}
