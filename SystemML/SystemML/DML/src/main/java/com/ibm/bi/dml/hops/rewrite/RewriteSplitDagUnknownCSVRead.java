/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2014
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.hops.rewrite;

import java.util.ArrayList;

import com.ibm.bi.dml.hops.DataOp;
import com.ibm.bi.dml.hops.Hop;
import com.ibm.bi.dml.hops.Hop.DataOpTypes;
import com.ibm.bi.dml.hops.Hop.FileFormatTypes;
import com.ibm.bi.dml.hops.Hop.VISIT_STATUS;
import com.ibm.bi.dml.hops.HopsException;
import com.ibm.bi.dml.parser.DataIdentifier;
import com.ibm.bi.dml.parser.StatementBlock;
import com.ibm.bi.dml.parser.VariableSet;

/**
 * Rule: Split Hop DAG after CSV reads with unknown size. This is
 * important to create recompile hooks if format is read from mtd
 * (we are not able to split it on statementblock creation) and 
 * mtd has unknown size (which can only happen for CSV). 
 * 
 */
public class RewriteSplitDagUnknownCSVRead extends StatementBlockRewriteRule
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2014\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";

	@Override
	public ArrayList<StatementBlock> rewriteStatementBlock(StatementBlock sb)
		throws HopsException 
	{
		ArrayList<StatementBlock> ret = new ArrayList<StatementBlock>();
		
		//collect all unknown csv reads hops
		ArrayList<Hop> cand = new ArrayList<Hop>();
		collectCSVReadHopsUnknownSize( sb.get_hops(), cand );
		
		//split hop dag on demand
		if( cand.size()>0 )
		{
			try
			{
				//duplicate sb incl live variable sets
				StatementBlock sb1 = new StatementBlock();
				sb1.setLiveIn(new VariableSet());
				sb1.setLiveOut(new VariableSet());
				
				//move csv reads incl reblock to new statement block
				//(and replace original persistent read with transient read)
				ArrayList<Hop> sb1hops = new ArrayList<Hop>();			
				for( Hop c : cand )
				{
					Hop reblock = c.getParent().get(0);
					long rlen = reblock.get_dim1();
					long clen = reblock.get_dim2();
					long nnz = reblock.getNnz();
					long brlen = reblock.get_rows_in_block();
					long bclen = reblock.get_cols_in_block();
	
					//create new transient read
					DataOp tread = new DataOp(reblock.get_name(), reblock.get_dataType(), reblock.get_valueType(),
		                    DataOpTypes.TRANSIENTREAD, null, rlen, clen, nnz, brlen, bclen);
					HopRewriteUtils.copyLineNumbers(reblock, tread);
					
					//replace reblock with transient read
					ArrayList<Hop> parents = new ArrayList<Hop>(reblock.getParent());
					for( int i=0; i<parents.size(); i++ )
					{
						Hop parent = parents.get(i);
						int pos = HopRewriteUtils.getChildReferencePos(parent, reblock);
						HopRewriteUtils.removeChildReferenceByPos(parent, reblock, pos);
						HopRewriteUtils.addChildReference(parent, tread, pos);
					}
					
					//add reblock sub dag to first statement block
					DataOp twrite = new DataOp(reblock.get_name(), reblock.get_dataType(), reblock.get_valueType(),
							                   reblock, DataOpTypes.TRANSIENTWRITE, null);
					twrite.setOutputParams(rlen, clen, nnz, brlen, bclen);
					HopRewriteUtils.copyLineNumbers(reblock, twrite);
					sb1hops.add(twrite);
					
					//update live in and out of new statement block (for piggybacking)
					DataIdentifier diVar = sb.variablesRead().getVariable(reblock.get_name()); 
					if( diVar != null ){ //var read should always exist because persistent read
						sb1.liveOut().addVariable(reblock.get_name(), new DataIdentifier(diVar));
						sb.liveIn().addVariable(reblock.get_name(), new DataIdentifier(diVar));
					}
				}
				
				sb1.set_hops(sb1hops);
				sb1.updateRecompilationFlag();
				ret.add(sb1); //statement block with csv reblocks
				ret.add(sb); //statement block with remaining hops
			}
			catch(Exception ex)
			{
				throw new HopsException("Failed to split hops dag for csv read with unknown size.", ex);
			}
		}
		//keep original hop dag
		else
		{
			ret.add(sb);
		}
		
		return ret;
	}
	
	/**
	 * 
	 * @param roots
	 * @param cand
	 */
	private void collectCSVReadHopsUnknownSize( ArrayList<Hop> roots, ArrayList<Hop> cand )
	{
		if( roots == null )
			return;
		
		Hop.resetVisitStatus(roots);
		for( Hop root : roots )
			collectCSVReadHopsUnknownSize(root, cand);
	}
	
	/**
	 * 
	 * @param root
	 * @param cand
	 */
	private void collectCSVReadHopsUnknownSize( Hop hop, ArrayList<Hop> cand )
	{
		if( hop.get_visited() == VISIT_STATUS.DONE )
			return;
		
		//collect persistent reads (of type csv, with unknown size)
		if( hop instanceof DataOp )
		{
			DataOp dop = (DataOp) hop;
			if(    dop.get_dataop() == DataOpTypes.PERSISTENTREAD
				&& dop.getFormatType() == FileFormatTypes.CSV
				&& !dop.dimsKnown()
				&& !HopRewriteUtils.hasOnlyTransientWriteParents(dop) )
			{
				cand.add(dop);
			}
		}
		
		//process children
		if( hop.getInput()!=null )
			for( Hop c : hop.getInput() )
				collectCSVReadHopsUnknownSize(c, cand);
		
		hop.set_visited(VISIT_STATUS.DONE);
	}
}