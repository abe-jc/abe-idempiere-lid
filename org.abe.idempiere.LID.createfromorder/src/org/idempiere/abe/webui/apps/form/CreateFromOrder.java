/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.idempiere.abe.webui.apps.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MRequisitionLine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;

/**
 * 
 * @author Fabian Aguilar faaguilar@gmail.com
 * @author Abraham Sulaeman abraham.sulaeman@gmail.com
 * - compatibility with Idempiere Release-9
 *
 */
public abstract class CreateFromOrder extends CreateFrom
{
	/**
	 *  Protected Constructor
	 *  @param mTab MTab
	 */
	public CreateFromOrder(GridTab mTab)
	{
		super(mTab);
		if (log.isLoggable(Level.INFO)) log.info(mTab.toString());
	}   //  VCreateFromInvoice

	/**
	 *  Dynamic Init
	 *  @return true if initialized
	 */
	public boolean dynInit() throws Exception
	{
		log.config("");
		setTitle(Msg.getElement(Env.getCtx(), "C_Order_ID", false) + " .. " + Msg.translate(Env.getCtx(), "CreateFrom"));

		return true;
	}   //  dynInit


	/**
	 *  Load Data - Shipment not invoiced
	 *  @param M_InOut_ID InOut
	 */
	protected Vector<Vector<Object>> getRequisitionData(Object Requisition, Object Org,  Object User)
	{

		/**
		 *  Selected        - 0
		 *  Qty             - 1
		 *  C_UOM_ID        - 2
		 *  DateRequested   - 3
		 *  M_Product_ID    - 4
		 *  ProductNo       - 5
		 *  RequisitionLine - 6
		 *  Description     - 7
		 *  Organization    - 8
		 *  User			- 9 
		 */
		//
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql = new StringBuilder("select r.M_Requisition_ID,r.DocumentNo||'-'||rl.Line,r.DateRequired,r.PriorityRule,COALESCE(rl.M_Product_ID,0),");   //1-5
				sql.append(" COALESCE(p.Name,c.Name) as ProductName,rl.Description, rl.Qty-NVL(po.QtyPO,0),rl.C_BPartner_ID, bp.Name as BpName, rl.M_RequisitionLine_ID, u.Name as Username, o.Name as OrgName,");  //6-13
				sql.append(" c.C_Charge_ID, c.name as ChargeName, ");// 14-15
				sql.append(" rl.C_UOM_ID,COALESCE(uom.UOMSymbol,uom.Name), COALESCE(p.Value,'-') ");// 16-18
				sql.append(" from M_Requisition r " );
				sql.append(" inner join M_RequisitionLine rl on (r.m_requisition_id=rl.m_requisition_id)" );
				sql.append(" inner join AD_User u on (r.AD_User_ID=u.AD_User_ID)" );
				sql.append(" inner join AD_Org o on (r.AD_Org_ID=o.AD_Org_ID)" );
				sql.append(" left outer join M_Product p on (rl.M_Product_ID=p.M_Product_ID)" );

		if (Env.isBaseLanguage(Env.getCtx(), "C_UOM"))
		    	sql.append(" LEFT OUTER JOIN C_UOM uom ON (rl.C_UOM_ID=uom.C_UOM_ID)");
		else
			 	sql.append(" LEFT OUTER JOIN C_UOM_Trl uom ON (rl.C_UOM_ID=uom.C_UOM_ID AND uom.AD_Language='")
			 		.append(Env.getAD_Language(Env.getCtx())).append("')");

				sql.append(" left outer join C_Charge c on (rl.C_Charge_ID=c.C_Charge_ID)" );
				sql.append(" left outer join C_BPartner bp on (rl.C_BPartner_ID=bp.C_BPartner_ID)" );
				sql.append(" left outer join (SELECT col.M_RequisitionLine_ID, SUM(col.QtyOrdered) QtyPO " );
				sql.append(" FROM C_OrderLine col INNER JOIN C_Order co ON (co.C_Order_ID=col.C_Order_ID AND co.DocStatus NOT IN ('VO','RE')) " );
				sql.append(" GROUP By col.M_RequisitionLine_ID ) po on (rl.M_RequisitionLine_ID=po.M_RequisitionLine_ID)" );
				sql.append(" where r.docstatus='CO' ");  //rl.C_OrderLine_ID is null
				sql.append(" and rl.Qty-NVL(po.QtyPO,0) > 0  ");  
		
		if(Requisition!=null)
			sql.append(" AND rl.M_Requisition_ID=?");
		if(Org!=null)
			sql.append(" AND r.AD_Org_ID=?");
		if(User!=null)
			sql.append(" AND r.AD_User_ID=?");
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			int i=1;
			pstmt = DB.prepareStatement(sql.toString(), null);
			if(Requisition!=null)
				pstmt.setInt(i++, (Integer)Requisition);
			if(Org!=null)
				pstmt.setInt(i++, (Integer)Org);
			if(User!=null)
				pstmt.setInt(i++, (Integer)User);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>(7);
				line.add(Boolean.FALSE);           //  0-Selection
				line.add(rs.getBigDecimal(8));     //  1-Qty
				KeyNamePair pp = new KeyNamePair(rs.getInt(16), rs.getString(17).trim());
				line.add(pp);                      //  2-UOM
				line.add(rs.getTimestamp(3));      //  3-DateRequired
				pp = new KeyNamePair(rs.getInt(5), rs.getString(6).trim());
				line.add(pp);				       //  4-Product or charge
				line.add(rs.getString(18)); 	   //  5-Product Value
				pp =  new KeyNamePair(rs.getInt(11), rs.getString(2).trim());
				line.add(pp);  					   //  6-DocumentNo Line iD
				line.add(rs.getString(7));         //  7-description
				line.add(rs.getString(13));  	   //  8-OrgName
				
				line.add(rs.getString(12).trim()); //  9-user
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		return data;
	}   //  loadShipment


	/**
	 *  List number of rows selected
	 */
	public void info(IMiniTable miniTable, IStatusBar statusBar)
	{

	}   //  infoInvoice

	protected void configureMiniTable (IMiniTable miniTable)
	{

		miniTable.setColumnClass(0, Boolean.class, false);      //  0-Selection
		miniTable.setColumnClass(1, BigDecimal.class, false);    //  1-Qty
		miniTable.setColumnClass(2, String.class, true);        //  2-C_UOM_ID
		miniTable.setColumnClass(3, Timestamp.class, true);     //  3-DateRequired
		miniTable.setColumnClass(4, String.class, true);        //  4-Product
		miniTable.setColumnClass(5, String.class, true);        //  5-Product Value
		miniTable.setColumnClass(6, String.class, true);        //  6-DocumentNo
		miniTable.setColumnClass(7, String.class, true);        //  7-Description
		miniTable.setColumnClass(8, String.class, true);        //  8-OrgName
		miniTable.setColumnClass(9, String.class, true);        //  9-User
		
		//  Table UI
		miniTable.autoSize();
	}

	/**
	 *  Save - Create Order Lines
	 *  @return true if saved
	 */
	public boolean save(IMiniTable miniTable, String trxName)
	{
		//  Order
		int C_Order_ID = ((Integer)getGridTab().getValue("C_Order_ID")).intValue();
		MOrder order = new MOrder (Env.getCtx(), C_Order_ID, trxName);
		if (log.isLoggable(Level.CONFIG)) log.config(order.toString());

		//  Lines
		for (int i = 0; i < miniTable.getRowCount(); i++)
		{
			if (((Boolean)miniTable.getValueAt(i, 0)).booleanValue())
			{
				

				KeyNamePair pp = (KeyNamePair)miniTable.getValueAt(i, 6);   //  1-documentno  line id
				int M_RequisitionLine_ID = pp.getKey();
				MRequisitionLine rLine = new MRequisitionLine (Env.getCtx(), M_RequisitionLine_ID, trxName);
				
				BigDecimal QtyEntered = (BigDecimal) miniTable.getValueAt(i, 1); // Qty	
				//	Create new Order Line
				MOrderLine m_orderLine = new MOrderLine (order);
				m_orderLine.setDatePromised(rLine.getDateRequired());
				if (rLine.getM_Product_ID() >0)
				{
					m_orderLine.setProduct(MProduct.get(Env.getCtx(), rLine.getM_Product_ID()));
					m_orderLine.setM_AttributeSetInstance_ID(rLine.getM_AttributeSetInstance_ID());
				}
				else
				{
					m_orderLine.setC_Charge_ID(rLine.getC_Charge_ID());
					
				}
				m_orderLine.setPriceActual(rLine.getPriceActual());
				m_orderLine.setAD_Org_ID(rLine.getAD_Org_ID());
				m_orderLine.setQty(QtyEntered); // rLine.getQty()
				m_orderLine.set_ValueOfColumn("M_RequisitionLine_ID", M_RequisitionLine_ID);
				m_orderLine.saveEx();
				
				//	Update Requisition Line
				rLine.setC_OrderLine_ID(m_orderLine.getC_OrderLine_ID());
				rLine.saveEx();
				
			}   //   if selected
		}   //  for all rows

		

		return true;
	}   //  saveInvoice

	protected Vector<String> getOISColumnNames()
	{
		/**
		 *  Selected        - 0
		 *  Qty             - 1
		 *  C_UOM_ID        - 2
		 *  DateRequested   - 3
		 *  M_Product_ID    - 4
		 *  ProductNo       - 5
		 *  RequisitionLine - 6
		 *  Description     - 7
		 *  Organization    - 8
		 *  User			- 9 
		 */
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(7);
	    columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "Qty"));
	    columnNames.add(Msg.translate(Env.getCtx(), "C_UOM_ID"));
	    columnNames.add(Msg.translate(Env.getCtx(), "DateRequired"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "M_Product_ID", false));
	    columnNames.add(Msg.getElement(Env.getCtx(), "Value"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Documentno"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "Description", false));
	    columnNames.add(Msg.getElement(Env.getCtx(), "AD_Org_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "AD_User_ID", false));
	    

	    return columnNames;
	}

}
