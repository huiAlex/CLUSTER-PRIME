/*

   Derby - Class org.apache.derby.impl.sql.execute.DMLWriteGeneratedColumnsResultSet

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.execute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.ResultSet;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.NoPutResultSet;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.NumberDataValue;
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.sql.execute.RowChanger;

/*
 * This class includes code for auto generated columns that can be shared
 *  by insert and update statements in the execution phase.
 */
abstract public class DMLWriteGeneratedColumnsResultSet extends DMLWriteResultSet
{    
	/**
	 * keeps track of autoincrement values that are generated by 
	 * getSetAutoincrementValues.
	 */
	protected DataValueDescriptor				aiCache[];
	
	protected String              identitySequenceUUIDString;

	protected	NoPutResultSet			sourceResultSet;

	//following is for jdbc3.0 feature auto generated keys resultset
	protected  ResultSet			autoGeneratedKeysResultSet;
	protected  TemporaryRowHolderImpl	autoGeneratedKeysRowsHolder;
	protected  int[]                   autoGeneratedKeysColumnIndexes;
	/**
	 * If set to true, implies that Derby has generated autoincrement 
	 * values for this (rep)resultset. During refresh for example, the 
	 * autoincrement values are not generated but sent from the source 
	 * to target or vice-versa.
	 */
	protected boolean autoincrementGenerated;
	protected long	  identityVal;  //support of IDENTITY_LOCAL_VAL function
	protected boolean setIdentity;

	/**
	 * Constructor
	 *
 	 * @param activation		an activation
	 *
 	 * @exception StandardException on error
	 */
	DMLWriteGeneratedColumnsResultSet(Activation activation)
		throws StandardException
	{
		this(activation, activation.getConstantAction());
	}

	DMLWriteGeneratedColumnsResultSet(Activation activation, ConstantAction constantAction)
		throws StandardException
	{
		super(activation, constantAction);
	}
	
    /**
     * getSetAutoincrementValue will get the autoincrement value of the 
     * columnPosition specified for the target table. If increment is 
     * non-zero we will also update the autoincrement value. 
     *
     * @param columnPosition	position of the column in the table (1-based)
     * @param increment			amount of increment. 
     *
     * @exception StandardException if anything goes wrong.
     */
    public NumberDataValue
    	getSetAutoincrementValue(int columnPosition, long increment)
    	throws StandardException {
        if (SanityManager.DEBUG) {
            // This method should be overriden by InsertResultSet and
            // UpdateResultSet, other shouldn't need it.
            SanityManager.NOTREACHED();
        }
		return null; 
    }
    
    public void saveAIcacheInformation(String schemaName,
    		String tableName, String[] columnNames) 
    throws StandardException{
        if (aiCache != null)
        {
            HashMap<String,Long> aiHashtable = new HashMap<String,Long>();
            int numColumns = aiCache.length;
            // this insert updated ai values, store them in some persistent
            // place so that I can see these values.
            for (int i = 0; i < numColumns; i++)
            {
                if (aiCache[i] == null)
                    continue;
                aiHashtable.put(AutoincrementCounter.makeIdentity(
                        schemaName,
                        tableName,
                        columnNames[i]),
                        aiCache[i].getLong());
            }
            InternalTriggerExecutionContext itec =
                (InternalTriggerExecutionContext)lcc.getTriggerExecutionContext();
            if (itec == null)
                lcc.copyHashtableToAIHT(aiHashtable);
            else
                itec.copyHashtableToAIHT(aiHashtable);
        }	

        /* autoGeneratedResultset for JDBC3. Nulled after statement execution 
        is over (ie after it is saved off in LocalSatement object) */
        if (activation.getAutoGeneratedKeysResultsetMode())
            autoGeneratedKeysResultSet = autoGeneratedKeysRowsHolder.getResultSet();
        else
            autoGeneratedKeysResultSet = null;
    }
    
    /*
    ** verify the auto-generated key columns list(ie there are no invalid
    ** column names or positions). This is done at execution time because
    ** for a precompiled insert statement, user can specify different column
    ** selections for auto-generated keys.
    */
    protected void verifyAutoGeneratedRScolumnsList(UUID targetUUID)
            throws StandardException{
        if(activation.getAutoGeneratedKeysResultsetMode())
        {
            int[]   agi = activation.getAutoGeneratedKeysColumnIndexes();
            if ( agi != null ) { 
                verifyAutoGeneratedColumnsIndexes( agi, targetUUID ); 
            } else
            {
                String[]    agc = activation.getAutoGeneratedKeysColumnNames();
                if ( agc != null ) { 
                    verifyAutoGeneratedColumnsNames( agc, targetUUID ); 
                }
            }
        }
    }

    /**
     * Verify that the auto-generated columns list (by position) has valid
     * column positions for the table.
     */
    private void verifyAutoGeneratedColumnsIndexes(
            int[] columnIndexes, UUID targetUUID)
        throws StandardException
    {
        int size = columnIndexes.length;
        TableDescriptor tabDesc = 
                lcc.getDataDictionary().getTableDescriptor(targetUUID);

        // all 1-based column ids.
        for (int i = 0; i < size; i++)
        {
            ColumnDescriptor cd = tabDesc.getColumnDescriptor(columnIndexes[i]);
            if (!verifyAutoGenColumn(cd))
            {
                throw StandardException.newException(
                    SQLState.LANG_INVALID_AUTOGEN_COLUMN_POSITION,
                    columnIndexes[i], tabDesc.getName());
            }
       }
    }

    /**
     * Verify that the auto-generated columns list (by name) has valid
     * column names for the table. If all the column names are valid,
     * convert column names array to corresponding column positions array
     * Save that column positions array in activation. We do this to 
     * simplify the rest of the logic(it only has to deal with column 
     * positions here after).
     *
     * @exception StandardException		Thrown on error if invalid column
     * name in the list.
     */
     private void verifyAutoGeneratedColumnsNames(String[] columnNames, UUID targetUUID)
		throws StandardException
     {
        int size = columnNames.length;
        int columnPositions[] = new int[size];
        
        TableDescriptor tabDesc = 
            lcc.getDataDictionary().getTableDescriptor(targetUUID);
        ColumnDescriptor cd;

        for (int i = 0; i < size; i++)
        {
            if (columnNames[i] == null)
            {
                throw StandardException.newException(
                    SQLState.LANG_INVALID_AUTOGEN_COLUMN_NAME,
                    columnNames[i], tabDesc.getName());
            }

            cd = tabDesc.getColumnDescriptor(columnNames[i]);
            if (!verifyAutoGenColumn(cd))
            {
                throw StandardException.newException(
                    SQLState.LANG_INVALID_AUTOGEN_COLUMN_NAME,
                    columnNames[i], tabDesc.getName());
            }

            columnPositions[i] = cd.getPosition();
        }
        activation.setAutoGeneratedKeysResultsetInfo(columnPositions, null);
	}

	/**
	 * Check that the received ColumnDescriptor corresponds to a column
	 * for which it is possible to fetch auto-generated keys.
	 */
	private boolean verifyAutoGenColumn(ColumnDescriptor cd)
	{
		/* Derby currently gets generated keys by calling the
		 * IDENTITY_VAL_LOCAL() function (see "getGeneratedKeys()"
		 * as defined on EmbedStatement).  That function only
		 * considers autoincrement columns.  So if the column
		 * specified by the user is not autoincrement, we return
		 * false.
		 */
		return ((cd != null) && cd.isAutoincrement());
	}
	
    protected void firstExecuteSpecialHandlingAutoGen(boolean firstExecute,
        RowChanger rowChanger, UUID targetUUID) 
            throws StandardException {
    if (firstExecute && activation.getAutoGeneratedKeysResultsetMode()) {
        ResultDescription rd;
        Properties properties = new Properties();
        autoGeneratedKeysColumnIndexes =
          	activation.getAutoGeneratedKeysColumnIndexes();

        // Get the properties on the old heap
        rowChanger.getHeapConglomerateController().getInternalTablePropertySet(properties);

        if (autoGeneratedKeysColumnIndexes != null) {
            // Use user-provided column positions array.
            autoGeneratedKeysColumnIndexes =
              	uniqueColumnPositionArray(autoGeneratedKeysColumnIndexes, targetUUID);
        } else {
            // Prepare array of auto-generated keys for the table since
            // user didn't provide any.
            autoGeneratedKeysColumnIndexes =
                generatedColumnPositionsArray(targetUUID);
        }

        rd = lcc.getLanguageFactory().getResultDescription(
            resultDescription, autoGeneratedKeysColumnIndexes);
        autoGeneratedKeysRowsHolder =
            new TemporaryRowHolderImpl(activation, properties, rd);
        }
    }

    /**
     * If user didn't provide columns list for auto-generated columns, then only include
     * columns with auto-generated values in the resultset. Those columns would be ones
     * with default value defined.
     */
    private int[] generatedColumnPositionsArray(UUID targetUUID)
        throws StandardException
    {
        TableDescriptor tabDesb = lcc.getDataDictionary().getTableDescriptor(targetUUID);
	    ColumnDescriptor cd;
        int size = tabDesb.getMaxColumnID();

        int[] generatedColumnPositionsArray = new int[size];
        Arrays.fill(generatedColumnPositionsArray, -1);
        int generatedColumnNumbers = 0;

        for (int i=0; i<size; i++) {
            cd = tabDesb.getColumnDescriptor(i+1);
            if (cd.isAutoincrement()) { //if the column has auto-increment value
                generatedColumnNumbers++;
                generatedColumnPositionsArray[i] = i+1;
            } else if (cd.getDefaultValue() != null || cd.getDefaultInfo() != null) {//default value
                generatedColumnNumbers++;
                generatedColumnPositionsArray[i] = i+1;
            }
        }
        int[] returnGeneratedColumnPositionsArray = new int[generatedColumnNumbers];

        for (int i=0, j=0; i<size; i++) {
            if (generatedColumnPositionsArray[i] != -1)
                returnGeneratedColumnPositionsArray[j++] = generatedColumnPositionsArray[i];
        }

        return returnGeneratedColumnPositionsArray;
        }

    /**
     * Remove duplicate columns from the array. Then use this array to generate a sub-set
     * of insert resultset to be returned for JDBC3.0 getGeneratedKeys() call.
     */
    private int[] uniqueColumnPositionArray(int[] columnIndexes, UUID targetUUID)
        throws StandardException
    {
        int size = columnIndexes.length;
        TableDescriptor tabDesc = lcc.getDataDictionary().getTableDescriptor(targetUUID);

        //create an array of integer (the array size = number of columns in table)
        // valid column positions are 1...getMaxColumnID()
        int[] uniqueColumnIndexes = new int[tabDesc.getMaxColumnID()];

        int uniqueColumnNumbers = 0;

        //At the end of following loop, the uniqueColumnIndexes elements will not be 0 for user
        //selected auto-generated columns.
        for (int i=0; i<size; i++) {
            if (uniqueColumnIndexes[columnIndexes[i] - 1] == 0) {
                uniqueColumnNumbers++;
                uniqueColumnIndexes[columnIndexes[i] - 1] = columnIndexes[i];
            }
        }
        int[] returnUniqueColumnIndexes = new int[uniqueColumnNumbers];

        //return just the column positions which are not marked 0 in the uniqueColumnIndexes array
        for (int i=0, j=0; i<uniqueColumnIndexes.length; i++) {
            if (uniqueColumnIndexes[i] != 0)
                returnUniqueColumnIndexes[j++] = uniqueColumnIndexes[i];
        }

        return returnUniqueColumnIndexes;
    }

    /**
     * Take the input row and return a new compact ExecRow
     * using the column positions provided in columnIndexes.
     * Copies references, no cloning.
     */
    protected ExecRow getCompactRow
    (
    	ExecRow 					inputRow, 
        int[] 						columnIndexes
    )
    throws StandardException
    {
        ExecRow outRow;
        int numInputCols = inputRow.nColumns();

        if (columnIndexes == null)
        {
            outRow = new ValueRow(numInputCols);
            Object[] src = inputRow.getRowArray();
            Object[] dst = outRow.getRowArray();
            System.arraycopy(src, 0, dst, 0, src.length);
            return outRow;
        }

        int numOutputCols = columnIndexes.length;

        outRow = new ValueRow(numOutputCols);
        for (int i = 0; i < numOutputCols; i++)
        {
            outRow.setColumn(i+1,
            	inputRow.getColumn(columnIndexes[i]));
        }

        return outRow;
    }

    @Override
    public ResultSet getAutoGeneratedKeysResultset()
    {
        return autoGeneratedKeysResultSet;
    }
}
