package org.idempiere.abe.webui.factory;

import org.compiere.grid.ICreateFrom;
import org.compiere.grid.ICreateFromFactory;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_M_InOut;
import org.compiere.util.Env;
import org.idempiere.abe.webui.apps.form.WCreateFromOrderUI;
import org.idempiere.abe.webui.apps.form.WCreateFromShipmentSOUI;

/**
 * 
 * @author Fabian Aguilar faaguilar@gmail.com
 * @author Abraham Sulaeman abraham.sulaeman@gmail.com
 * - compatibility with Idempiere Release-9
 *
 */
public class FaaCreateFromFactory implements ICreateFromFactory {
	@Override
	public ICreateFrom create(GridTab mTab) 
	{
		String tableName = mTab.getTableName();
		String tabName = mTab.getName();
		String moveType;
		
		GridField field = mTab.getField("MovementType"); 
		if (field != null) 
			moveType = field.getValue().toString(); 
		else 
			moveType = Env.getContext(Env.getCtx(), mTab.getWindowNo(), "MovementType");
		
		if (tableName.equals(I_C_Order.Table_Name))
			return new WCreateFromOrderUI(mTab);
		
		if (tableName.equals(I_M_InOut.Table_Name) & moveType.equals("C-"))
			return new WCreateFromShipmentSOUI(mTab);		
		return null;
	}
}
