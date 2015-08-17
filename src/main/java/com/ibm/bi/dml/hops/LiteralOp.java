/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2015
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.hops;

import com.ibm.bi.dml.lops.Data;
import com.ibm.bi.dml.lops.Lop;
import com.ibm.bi.dml.lops.LopProperties.ExecType;
import com.ibm.bi.dml.lops.LopsException;
import com.ibm.bi.dml.parser.Expression.DataType;
import com.ibm.bi.dml.parser.Expression.ValueType;
import com.ibm.bi.dml.runtime.util.UtilFunctions;


public class LiteralOp extends Hop 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2015\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	private double value_double = Double.NaN;
	private long value_long = Long.MAX_VALUE;
	private String value_string;
	private boolean value_boolean;

	// INT, DOUBLE, STRING, BOOLEAN

	private LiteralOp() {
		//default constructor for clone
	}
	
	public LiteralOp(String l, double value) {
		super(l, DataType.SCALAR, ValueType.DOUBLE);
		this.value_double = value;
	}

	public LiteralOp(String l, long value) {
		super(l, DataType.SCALAR, ValueType.INT);
		this.value_long = value;
	}

	public LiteralOp(String l, String value) {
		super(l, DataType.SCALAR, ValueType.STRING);
		this.value_string = value;
	}

	public LiteralOp(String l, boolean value) {
		super(l, DataType.SCALAR, ValueType.BOOLEAN);
		this.value_boolean = value;
	}

	
	@Override
	public Lop constructLops()
		throws HopsException, LopsException  
	{	
		//return already created lops
		if( getLops() != null )
			return getLops();

		
		try 
		{
			Lop l = null;

			switch (getValueType()) {
			case DOUBLE:
				l = Data.createLiteralLop(ValueType.DOUBLE, Double.toString(value_double));
				break;
			case BOOLEAN:
				l = Data.createLiteralLop(ValueType.BOOLEAN, Boolean.toString(value_boolean));
				break;
			case STRING:
				l = Data.createLiteralLop(ValueType.STRING, value_string);
				break;
			case INT:
				l = Data.createLiteralLop(ValueType.INT, Long.toString(value_long));
				break;
			default:
				throw new HopsException(this.printErrorLocation() + 
						"unexpected value type constructing lops for LiteralOp.\n");
			}

			l.getOutputParameters().setDimensions(0, 0, 0, 0, -1);
			setLineNumbers(l);
			setLops(l);
		} 
		catch(LopsException e) {
			throw new HopsException(e);
		}
		
		//note: no reblock lop because always scalar
		
		return getLops();
	}

	public void printMe() throws HopsException {
		if (LOG.isDebugEnabled()){
			if (getVisited() != VisitStatus.DONE) {
				super.printMe();
				switch (getValueType()) {
				case DOUBLE:
					LOG.debug("  Value: " + value_double);
					break;
				case BOOLEAN:
					LOG.debug("  Value: " + value_boolean);
					break;
				case STRING:
					LOG.debug("  Value: " + value_string);
					break;
				case INT:
					LOG.debug("  Value: " + value_long);
					break;
				default:
					throw new HopsException(this.printErrorLocation() +
							"unexpected value type printing LiteralOp.\n");
				}

				for (Hop h : getInput()) {
					h.printMe();
				}

			}
			setVisited(VisitStatus.DONE);
		}
	}

	@Override
	public String getOpString() {
		String val = null;
		switch (getValueType()) {
			case DOUBLE:
				val = Double.toString(value_double);
				break;
			case BOOLEAN:
				val = Boolean.toString(value_boolean);
				break;
			case STRING:
				val = value_string;
				break;
			case INT:
				val = Long.toString(value_long);
				break;
			default:
				val = "";
		}
		return "LiteralOp " + val;
	}
		
	@Override
	protected double computeOutputMemEstimate( long dim1, long dim2, long nnz )
	{		
		double ret = 0;
		
		switch( getValueType() ) {
			case INT:
				ret = OptimizerUtils.INT_SIZE; break;
			case DOUBLE:
				ret = OptimizerUtils.DOUBLE_SIZE; break;
			case BOOLEAN:
				ret = OptimizerUtils.BOOLEAN_SIZE; break;
			case STRING: 
				ret = this.value_string.length() * OptimizerUtils.CHAR_SIZE; break;
			case OBJECT:
				ret = OptimizerUtils.DEFAULT_SIZE; break;
			default:
				ret = 0;
		}
		
		return ret;
	}
	
	@Override
	protected double computeIntermediateMemEstimate( long dim1, long dim2, long nnz )
	{
		return 0;
	}
	
	@Override
	protected long[] inferOutputCharacteristics( MemoTable memo )
	{
		return null;
	}
	
	@Override
	public boolean allowsAllExecTypes()
	{
		return false;
	}	
	
	@Override
	protected ExecType optFindExecType() throws HopsException {
		// Since a Literal hop does not represent any computation, 
		// this function is not applicable. 
		return null;
	}
	
	@Override
	public void refreshSizeInformation()
	{
		//do nothing; it is a scalar
	}
	
	public long getLongValue() throws HopsException 
	{
		switch( getValueType() ) {
			case INT:		
				return value_long;
			case DOUBLE:	
				return UtilFunctions.toLong(value_double);
			case STRING:
				return Long.parseLong(value_string);	
			default:
				throw new HopsException("Can not coerce an object of type " + getValueType() + " into Long.");
		}
	}
	
	public double getDoubleValue() throws HopsException {
		switch( getValueType() ) {
			case INT:		
				return value_long;
			case DOUBLE:	
				return value_double;
			case STRING:
				return Double.parseDouble(value_string);
			default:
				throw new HopsException("Can not coerce an object of type " + getValueType() + " into Double.");
		}
	}
	
	public boolean getBooleanValue() throws HopsException {
		if ( getValueType() == ValueType.BOOLEAN ) {
			return value_boolean;
		}
		else
			throw new HopsException("Can not coerce an object of type " + getValueType() + " into Boolean.");
	}
	
	public String getStringValue() 
	{
		switch( getValueType() ) {
			case BOOLEAN:
				return String.valueOf(value_boolean);
			case INT:		
				return String.valueOf(value_long);
			case DOUBLE:	
				return String.valueOf(value_double);
			case STRING:
				return value_string;
			case OBJECT:
			case UNKNOWN:	
				//do nothing (return null)
		}
		
		return null;
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException 
	{
		LiteralOp ret = new LiteralOp();	
		
		//copy generic attributes
		ret.clone(this, false);
		
		//copy specific attributes
		ret.value_double = value_double;
		ret.value_long = value_long;
		ret.value_string = value_string;
		ret.value_boolean = value_boolean;
		
		return ret;
	}
	
	@Override
	public boolean compare( Hop that )
	{
		return false;
	}
}