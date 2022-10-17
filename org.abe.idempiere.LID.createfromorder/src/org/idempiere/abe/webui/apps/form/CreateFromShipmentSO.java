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
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.grid.CreateFromShipment;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MRMALine;
import org.compiere.model.MRequisitionLine;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
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
public abstract class CreateFromShipmentSO extends CreateFromShipment
{
	private int defaultLocator_ID=0;
	/**
	 *  Protected Constructor
	 *  @param mTab MTab
	 */
	public CreateFromShipmentSO(GridTab mTab)
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
		setTitle(Msg.getElement(Env.getCtx(), "M_InOut_ID", isSOTrx) + " .. " + Msg.translate(Env.getCtx(), "CreateFrom"));

		return true;
	}   //  dynInit


	/**
	 *  Load Data - Order
	 *  @param C_Order_ID Order
	 *  @param forInvoice true if for invoice vs. delivery qty
	 */
	protected Vector<Vector<Object>> getOrderData (int C_Order_ID, boolean forInvoice)
	{
		/**
		 *  Selected        - 0
		 *  Qty             - 1
		 *  C_UOM_ID        - 2
		 *  M_Locator_ID    - 3
		 *  M_Product_ID    - 4
		 *  VendorProductNo - 5
		 *  OrderLine       - 6
		 *  ShipmentLine    - 7
		 *  InvoiceLine     - 8
		 */
		if (log.isLoggable(Level.CONFIG)) log.config("C_Order_ID=" + C_Order_ID);
		p_order = new MOrder (Env.getCtx(), C_Order_ID, null);      //  save
		Vector<Vector<Object>> data = new Vector<Vector<Object>>();
		StringBuilder sql;
		isSOTrx = p_order.isSOTrx();
		
		if (!p_order.isSOTrx()) {
			sql = new StringBuilder("SELECT "
					+ "l.QtyOrdered-SUM(COALESCE(m.Qty,0))"
					// subtract drafted lines from this or other orders IDEMPIERE-2889
					+ "-COALESCE((SELECT SUM(MovementQty) FROM M_InOutLine iol JOIN M_InOut io ON iol.M_InOut_ID=io.M_InOut_ID WHERE l.C_OrderLine_ID=iol.C_OrderLine_ID AND io.Processed='N'),0),"	//	1
					+ "CASE WHEN l.QtyOrdered=0 THEN 0 ELSE l.QtyEntered/l.QtyOrdered END,"	//	2
					+ " l.C_UOM_ID,COALESCE(uom.UOMSymbol,uom.Name),"			//	3..4
					+ " p.M_Locator_ID, loc.Value, " // 5..6
					+ " COALESCE(l.M_Product_ID,0),COALESCE(p.Name,c.Name), " //	7..8
					+ " po.VendorProductNo, " // 9
					+ " l.C_OrderLine_ID,l.Line "	//	10..11
					+ "FROM C_OrderLine l"
					+ " LEFT OUTER JOIN M_Product_PO po ON (l.M_Product_ID = po.M_Product_ID AND l.C_BPartner_ID = po.C_BPartner_ID) "
					+ " LEFT OUTER JOIN M_MatchPO m ON (l.C_OrderLine_ID=m.C_OrderLine_ID AND ");
			sql.append(forInvoice ? "m.C_InvoiceLine_ID" : "m.M_InOutLine_ID");
			sql.append(" IS NOT NULL)")
			.append(" LEFT OUTER JOIN M_Product p ON (l.M_Product_ID=p.M_Product_ID)"
					+ " LEFT OUTER JOIN M_Locator loc on (p.M_Locator_ID=loc.M_Locator_ID)"
					+ " LEFT OUTER JOIN C_Charge c ON (l.C_Charge_ID=c.C_Charge_ID)");
			
			if (Env.isBaseLanguage(Env.getCtx(), "C_UOM"))
				sql.append(" LEFT OUTER JOIN C_UOM uom ON (l.C_UOM_ID=uom.C_UOM_ID)");
			else
				sql.append(" LEFT OUTER JOIN C_UOM_Trl uom ON (l.C_UOM_ID=uom.C_UOM_ID AND uom.AD_Language='")
				.append(Env.getAD_Language(Env.getCtx())).append("')");
			//
			sql.append(" WHERE l.C_Order_ID=? "			//	#1
					+ "GROUP BY l.QtyOrdered,CASE WHEN l.QtyOrdered=0 THEN 0 ELSE l.QtyEntered/l.QtyOrdered END, "
					+ "l.C_UOM_ID,COALESCE(uom.UOMSymbol,uom.Name), p.M_Locator_ID, loc.Value, po.VendorProductNo, "
					+ "l.M_Product_ID,COALESCE(p.Name,c.Name), l.Line,l.C_OrderLine_ID "
					+ "ORDER BY l.Line");
			
		}
		else {
			// CreateFrom at Window Shipment
			sql = new StringBuilder("SELECT "
					+ "l.QtyReserved"
					// subtract drafted lines from this or other orders IDEMPIERE-2889
					+ "-COALESCE((SELECT SUM(MovementQty) FROM M_InOutLine iol JOIN M_InOut io ON iol.M_InOut_ID=io.M_InOut_ID WHERE l.C_OrderLine_ID=iol.C_OrderLine_ID AND io.Processed='N'),0),"	//	1
					+ "CASE WHEN l.QtyOrdered=0 THEN 0 ELSE l.QtyEntered/l.QtyOrdered END,"	//	2
					+ " l.C_UOM_ID,COALESCE(uom.UOMSymbol,uom.Name),"			//	3..4
					+ " p.M_Locator_ID, loc.Value, " // 5..6
					+ " COALESCE(l.M_Product_ID,0),COALESCE(p.Name,c.Name), " //	7..8
					+ " COALESCE(po.VendorProductNo, p.Value), " // 9
					+ " l.C_OrderLine_ID,l.Line, "	//	10..11
					+ " l.DatePromised, "	//	12
					+ " p.value, " // 13
					+ " COALESCE(p.sku,'-') " // 14
					+ "FROM C_OrderLine l"
					+ " LEFT OUTER JOIN C_BPartner_Product po ON (l.M_Product_ID = po.M_Product_ID AND l.C_BPartner_ID = po.C_BPartner_ID) ");
			sql.append(" LEFT OUTER JOIN M_Product p ON (l.M_Product_ID=p.M_Product_ID)"
					+ " LEFT OUTER JOIN M_Locator loc on (p.M_Locator_ID=loc.M_Locator_ID)"
					+ " LEFT OUTER JOIN C_Charge c ON (l.C_Charge_ID=c.C_Charge_ID)");
			
			if (Env.isBaseLanguage(Env.getCtx(), "C_UOM"))
				sql.append(" LEFT OUTER JOIN C_UOM uom ON (l.C_UOM_ID=uom.C_UOM_ID)");
			else
				sql.append(" LEFT OUTER JOIN C_UOM_Trl uom ON (l.C_UOM_ID=uom.C_UOM_ID AND uom.AD_Language='")
				.append(Env.getAD_Language(Env.getCtx())).append("')");
			//
			sql.append(" WHERE l.C_Order_ID=? "			//	#1
					+ " AND l.QtyReserved "
					+ " -COALESCE((SELECT SUM(MovementQty) FROM M_InOutLine iol JOIN M_InOut io ON iol.M_InOut_ID=io.M_InOut_ID WHERE l.C_OrderLine_ID=iol.C_OrderLine_ID AND io.Processed='N'),0) > 0 "	//	1
					+ " ORDER BY l.Line");
			
		}
			
		//
		if (log.isLoggable(Level.FINER)) log.finer(sql.toString());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, C_Order_ID);
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				Vector<Object> line = new Vector<Object>();
				line.add(Boolean.FALSE);           //  0-Selection
				if (!p_order.isSOTrx()) { //if Purchase Order
					BigDecimal qtyOrdered = rs.getBigDecimal(1);
					BigDecimal multiplier = rs.getBigDecimal(2);
					BigDecimal qtyEntered = qtyOrdered.multiply(multiplier);
					line.add(qtyEntered);  //  1-Qty
				}
				else { //if Sales Order
					BigDecimal qtyReserved = rs.getBigDecimal(1);
					line.add(qtyReserved);  //  1-Qty
				}
				KeyNamePair pp = new KeyNamePair(rs.getInt(3), rs.getString(4).trim());
				line.add(pp);                           //  2-UOM
				// Add product
				line.add(rs.getString(13));				// 3-Search Key
				line.add(rs.getString(14));				// 4-SKU
				line.add(rs.getTimestamp(12));			//  5-DatePromised
				pp = new KeyNamePair(rs.getInt(7), rs.getString(8));
				line.add(pp);                           //  6-Product Name
				line.add(rs.getString(9));				// 7-VendorProductNo
				pp = new KeyNamePair(rs.getInt(10), rs.getString(11));
				line.add(pp);                           //  8-OrderLine
				// Add locator
				line.add(getLocatorKeyNamePair(rs.getInt(5)));// 9-Locator
				//line.add(null);                         //  7-Ship
				//line.add(null);                         //  8-Invoice
				data.add(line);
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
			//throw new DBException(e, sql.toString());
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		return data;
	}   //  LoadOrder


	/**
	 *  Save - Create Invoice Lines
	 *  @return true if saved
	 */
	public boolean save(IMiniTable miniTable, String trxName)
	{
		/*
		dataTable.stopEditor(true);
		log.config("");
		TableModel model = dataTable.getModel();
		int rows = model.getRowCount();
		if (rows == 0)
			return false;
		//
		Integer defaultLoc = (Integer) locatorField.getValue();
		if (defaultLoc == null || defaultLoc.intValue() == 0) {
			locatorField.setBackground(AdempierePLAF.getFieldBackground_Error());
			return false;
		}
		*/
		int M_Locator_ID = defaultLocator_ID;
		if (M_Locator_ID == 0) {
			return false;
		}
		// Get Shipment
		int M_InOut_ID = ((Integer) getGridTab().getValue("M_InOut_ID")).intValue();
		MInOut inout = new MInOut(Env.getCtx(), M_InOut_ID, trxName);
		if (log.isLoggable(Level.CONFIG)) log.config(inout + ", C_Locator_ID=" + M_Locator_ID);

		// Lines
		for (int i = 0; i < miniTable.getRowCount(); i++)
		{
			if (((Boolean)miniTable.getValueAt(i, 0)).booleanValue()) {
				// variable values
				BigDecimal QtyEntered = (BigDecimal) miniTable.getValueAt(i, 1); // Qty
				KeyNamePair pp = (KeyNamePair) miniTable.getValueAt(i, 2); // UOM
				int C_UOM_ID = pp.getKey();
				pp = (KeyNamePair) miniTable.getValueAt(i, 9); // Locator
				// If a locator is specified on the product, choose that otherwise default locator
				M_Locator_ID = pp!=null && pp.getKey()!=0 ? pp.getKey() : defaultLocator_ID;

				pp = (KeyNamePair) miniTable.getValueAt(i, 6); // Product
				int M_Product_ID = pp.getKey();
				int C_OrderLine_ID = 0;
				pp = (KeyNamePair) miniTable.getValueAt(i, 8); // OrderLine
				if (pp != null)
					C_OrderLine_ID = pp.getKey();
				int M_RMALine_ID = 0;
				//pp = (KeyNamePair) miniTable.getValueAt(i, 7); // RMA
				// If we have RMA
				//if (pp != null)
				//	M_RMALine_ID = pp.getKey();
				int C_InvoiceLine_ID = 0;
				MInvoiceLine il = null;
				//pp = (KeyNamePair) miniTable.getValueAt(i, 8); // InvoiceLine
				//if (pp != null)
				//	C_InvoiceLine_ID = pp.getKey();
				if (C_InvoiceLine_ID != 0)
					il = new MInvoiceLine (Env.getCtx(), C_InvoiceLine_ID, trxName);
				//boolean isInvoiced = (C_InvoiceLine_ID != 0);
				//	Precision of Qty UOM
				int precision = 2;
				if (M_Product_ID != 0)
				{
					MProduct product = MProduct.get(Env.getCtx(), M_Product_ID);
					precision = product.getUOMPrecision();
				}
				QtyEntered = QtyEntered.setScale(precision, RoundingMode.HALF_DOWN);
				//
				if (log.isLoggable(Level.FINE)) log.fine("Line QtyEntered=" + QtyEntered
						+ ", Product=" + M_Product_ID 
						+ ", OrderLine=" + C_OrderLine_ID + ", InvoiceLine=" + C_InvoiceLine_ID);

				//	Credit Memo - negative Qty
				if (m_invoice != null && m_invoice.isCreditMemo() )
					QtyEntered = QtyEntered.negate();

				//	Create new InOut Line
				MInOutLine iol = new MInOutLine (inout);
				iol.setM_Product_ID(M_Product_ID, C_UOM_ID);	//	Line UOM
				iol.setQty(QtyEntered);							//	Movement/Entered
				//
				MOrderLine ol = null;
				MRMALine rmal = null;
				if (C_OrderLine_ID != 0)
				{
					iol.setC_OrderLine_ID(C_OrderLine_ID);
					ol = new MOrderLine (Env.getCtx(), C_OrderLine_ID, trxName);
					if (ol.getQtyEntered().compareTo(ol.getQtyOrdered()) != 0)
					{
						iol.setMovementQty(QtyEntered
								.multiply(ol.getQtyOrdered())
								.divide(ol.getQtyEntered(), 12, RoundingMode.HALF_UP));
						iol.setC_UOM_ID(ol.getC_UOM_ID());
					}
					iol.setM_AttributeSetInstance_ID(ol.getM_AttributeSetInstance_ID());
					iol.setDescription(ol.getDescription());
					//
					iol.setC_Project_ID(ol.getC_Project_ID());
					iol.setC_ProjectPhase_ID(ol.getC_ProjectPhase_ID());
					iol.setC_ProjectTask_ID(ol.getC_ProjectTask_ID());
					iol.setC_Activity_ID(ol.getC_Activity_ID());
					iol.setC_Campaign_ID(ol.getC_Campaign_ID());
					iol.setAD_OrgTrx_ID(ol.getAD_OrgTrx_ID());
					iol.setUser1_ID(ol.getUser1_ID());
					iol.setUser2_ID(ol.getUser2_ID());
				}
				else if (il != null)
				{
					if (il.getQtyEntered().compareTo(il.getQtyInvoiced()) != 0)
					{
						iol.setMovementQty(QtyEntered
								.multiply(il.getQtyInvoiced())
								.divide(il.getQtyEntered(), 12, RoundingMode.HALF_UP));
						iol.setC_UOM_ID(il.getC_UOM_ID());
					}
					iol.setDescription(il.getDescription());
					iol.setC_Project_ID(il.getC_Project_ID());
					iol.setC_ProjectPhase_ID(il.getC_ProjectPhase_ID());
					iol.setC_ProjectTask_ID(il.getC_ProjectTask_ID());
					iol.setC_Activity_ID(il.getC_Activity_ID());
					iol.setC_Campaign_ID(il.getC_Campaign_ID());
					iol.setAD_OrgTrx_ID(il.getAD_OrgTrx_ID());
					iol.setUser1_ID(il.getUser1_ID());
					iol.setUser2_ID(il.getUser2_ID());
				}
				else if (M_RMALine_ID != 0)
				{
					rmal = new MRMALine(Env.getCtx(), M_RMALine_ID, trxName);
					iol.setM_RMALine_ID(M_RMALine_ID);
					iol.setQtyEntered(QtyEntered);
					iol.setDescription(rmal.getDescription());
					iol.setM_AttributeSetInstance_ID(rmal.getM_AttributeSetInstance_ID());
					iol.setC_Project_ID(rmal.getC_Project_ID());
					iol.setC_ProjectPhase_ID(rmal.getC_ProjectPhase_ID());
					iol.setC_ProjectTask_ID(rmal.getC_ProjectTask_ID());
					iol.setC_Activity_ID(rmal.getC_Activity_ID());
					iol.setAD_OrgTrx_ID(rmal.getAD_OrgTrx_ID());
					iol.setUser1_ID(rmal.getUser1_ID());
					iol.setUser2_ID(rmal.getUser2_ID());
				}

				//	Charge
				if (M_Product_ID == 0)
				{
					if (ol != null && ol.getC_Charge_ID() != 0)			//	from order
						iol.setC_Charge_ID(ol.getC_Charge_ID());
					else if (il != null && il.getC_Charge_ID() != 0)	//	from invoice
						iol.setC_Charge_ID(il.getC_Charge_ID());
					else if (rmal != null && rmal.getC_Charge_ID() != 0) // from rma
						iol.setC_Charge_ID(rmal.getC_Charge_ID());
				}
				// Set locator
				iol.setM_Locator_ID(M_Locator_ID);
				iol.saveEx();
				//	Create Invoice Line Link
				if (il != null)
				{
					il.setM_InOutLine_ID(iol.getM_InOutLine_ID());
					il.saveEx();
				}
			}   //   if selected
		}   //  for all rows

		/**
		 *  Update Header
		 *  - if linked to another order/invoice/rma - remove link
		 *  - if no link set it
		 */
		if (p_order != null && p_order.getC_Order_ID() != 0)
		{
			inout.setC_Order_ID (p_order.getC_Order_ID());
			inout.setAD_OrgTrx_ID(p_order.getAD_OrgTrx_ID());
			inout.setC_Project_ID(p_order.getC_Project_ID());
			inout.setC_Campaign_ID(p_order.getC_Campaign_ID());
			inout.setC_Activity_ID(p_order.getC_Activity_ID());
			inout.setUser1_ID(p_order.getUser1_ID());
			inout.setUser2_ID(p_order.getUser2_ID());

			if ( p_order.isDropShip() )
			{
				inout.setM_Warehouse_ID( p_order.getM_Warehouse_ID() );
				inout.setIsDropShip(p_order.isDropShip());
				inout.setDropShip_BPartner_ID(p_order.getDropShip_BPartner_ID());
				inout.setDropShip_Location_ID(p_order.getDropShip_Location_ID());
				inout.setDropShip_User_ID(p_order.getDropShip_User_ID());
			}
		}
		if (m_invoice != null && m_invoice.getC_Invoice_ID() != 0)
		{
			if (inout.getC_Order_ID() == 0)
				inout.setC_Order_ID (m_invoice.getC_Order_ID());
			inout.setC_Invoice_ID (m_invoice.getC_Invoice_ID());
			inout.setAD_OrgTrx_ID(m_invoice.getAD_OrgTrx_ID());
			inout.setC_Project_ID(m_invoice.getC_Project_ID());
			inout.setC_Campaign_ID(m_invoice.getC_Campaign_ID());
			inout.setC_Activity_ID(m_invoice.getC_Activity_ID());
			inout.setUser1_ID(m_invoice.getUser1_ID());
			inout.setUser2_ID(m_invoice.getUser2_ID());
		}
		if (m_rma != null && m_rma.getM_RMA_ID() != 0)
		{
			MInOut originalIO = m_rma.getShipment();
			inout.setIsSOTrx(m_rma.isSOTrx());
			inout.setC_Order_ID(0);
			inout.setC_Invoice_ID(0);
			inout.setM_RMA_ID(m_rma.getM_RMA_ID());
			inout.setAD_OrgTrx_ID(originalIO.getAD_OrgTrx_ID());
			inout.setC_Project_ID(originalIO.getC_Project_ID());
			inout.setC_Campaign_ID(originalIO.getC_Campaign_ID());
			inout.setC_Activity_ID(originalIO.getC_Activity_ID());
			inout.setUser1_ID(originalIO.getUser1_ID());
			inout.setUser2_ID(originalIO.getUser2_ID());
		}
		inout.saveEx();
		return true;		

	}   //  saveInvoice

	protected Vector<String> getOISColumnNames()
	{
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(10);
	    columnNames.add(Msg.getMsg(Env.getCtx(), "Select"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Quantity"));
	    columnNames.add(Msg.translate(Env.getCtx(), "C_UOM_ID"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Value"));
	    columnNames.add(Msg.translate(Env.getCtx(), "SKU"));
	    columnNames.add(Msg.translate(Env.getCtx(), "DatePromised"));
	    columnNames.add(Msg.translate(Env.getCtx(), "M_Product_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "VendorProductNo", isSOTrx));
	    columnNames.add(Msg.getElement(Env.getCtx(), "C_Order_ID", isSOTrx));
	    columnNames.add(Msg.translate(Env.getCtx(), "M_Locator_ID"));
	    //columnNames.add(Msg.getElement(Env.getCtx(), "M_RMA_ID", isSOTrx));
	    //columnNames.add(Msg.getElement(Env.getCtx(), "C_Invoice_ID", isSOTrx));
	    
	    return columnNames;
	}

	protected void configureMiniTable (IMiniTable miniTable)
	{
		miniTable.setColumnClass(0, Boolean.class, false);     	//  Selection
		miniTable.setColumnClass(1, BigDecimal.class, false);   //  Qty
		miniTable.setColumnClass(2, String.class, true);        //  UOM
		miniTable.setColumnClass(3, String.class, true);  		//  Value
		miniTable.setColumnClass(4, String.class, true);  		//  SKU
		miniTable.setColumnClass(5, Timestamp.class, true);     //  DatePromised
		miniTable.setColumnClass(6, String.class, true);   		//  Product
		miniTable.setColumnClass(7, String.class, true); 		//  VendorProductNo
		miniTable.setColumnClass(8, String.class, true);     	//  Order
		miniTable.setColumnClass(9, String.class, true);     	//  locator
		
		//  Table UI
		miniTable.autoSize();
		
	}
	
	protected Vector<Vector<Object>> getOrderData (int C_Order_ID, boolean forInvoice, int M_Locator_ID)
	{
		defaultLocator_ID = M_Locator_ID;
		return getOrderData (C_Order_ID, forInvoice);
	}	

	/**
	 *  Load PBartner dependent Order/Invoice/Shipment Field.
	 *  @param C_BPartner_ID BPartner
	 *  @param forInvoice for invoice
	 */
	protected ArrayList<KeyNamePair> loadOrderData (int C_BPartner_ID, boolean forInvoice, boolean sameWarehouseOnly)
	{
		return loadOrderData(C_BPartner_ID, forInvoice, sameWarehouseOnly, false);
	}
	
	protected ArrayList<KeyNamePair> loadOrderData (int C_BPartner_ID, boolean forInvoice, boolean sameWarehouseOnly, boolean forCreditMemo)
	{
		ArrayList<KeyNamePair> list = new ArrayList<KeyNamePair>();

		String isSOTrxParam = isSOTrx ? "Y":"N";
		//	Display
		StringBuilder display = new StringBuilder("o.DocumentNo||' - ' ||")
			.append(DB.TO_CHAR("o.DateOrdered", DisplayType.Date, Env.getAD_Language(Env.getCtx())))
			.append("||' _ '||")
			.append("NVL(o.POReference,'-')");
		//
		String column = "ol.QtyDelivered";
		String colBP = "o.C_BPartner_ID";
		if (forInvoice)
		{
			column = "ol.QtyInvoiced";
			colBP = "o.Bill_BPartner_ID";
		}
		StringBuilder sql = new StringBuilder("SELECT o.C_Order_ID,")
			.append(display)
			.append(" FROM C_Order o WHERE ")
			.append(colBP)
			.append("=? AND o.IsSOTrx=? AND o.DocStatus IN ('CL','CO') AND o.C_Order_ID IN (SELECT ol.C_Order_ID FROM C_OrderLine ol WHERE ");
		if (forCreditMemo)
			sql.append(column).append(">0 AND (CASE WHEN ol.QtyDelivered>=ol.QtyOrdered THEN ol.QtyDelivered-ol.QtyInvoiced!=0 ELSE 1=1 END)) ");
		else
			sql.append("ol.QtyOrdered-").append(column).append("!=0) ");
					
		if(sameWarehouseOnly)
		{
			sql = sql.append(" AND o.M_Warehouse_ID=? ");
		}
		if (forCreditMemo)
			sql = sql.append("ORDER BY o.DateOrdered DESC,o.DocumentNo DESC");
		else
			sql = sql.append("ORDER BY o.DateOrdered,o.DocumentNo");
		//
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, C_BPartner_ID);
			pstmt.setString(2, isSOTrxParam);
			if(sameWarehouseOnly)
			{
				//only active for material receipts
				pstmt.setInt(3, getM_Warehouse_ID());
			}
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				list.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
			}
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql.toString(), e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}

		return list;
	}   //  initBPartnerOIS
	
}
