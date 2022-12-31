package org.idempiere.abe.webui.apps.component;

import org.adempiere.webui.component.Bandbox;
import org.adempiere.webui.component.ListItem;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.util.KeyNamePair;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Bandpopup;

public class BandboxSearch extends Bandbox {

	private static final long serialVersionUID = -245144208407178811L;
	private Bandpopup bandpopup;
	private Listbox listbox;
	
	public BandboxSearch() {
		init();
	}
	
	private void init() {
		this.setAutodrop(true);
		
		bandpopup = new Bandpopup();
		this.appendChild(bandpopup);
		
		listbox = new Listbox();
		ZKUpdateUtil.setWidth(listbox, "300px");
		bandpopup.appendChild(listbox);
	}
	
	public void clearSelection() {
		listbox.setSelectedIndex(-1);
		this.setRawValue(null);
	}
	
	public Listbox getListbox() {
		return listbox;
	}
	
	public ListItem getSelectedItem() {
		return listbox.getSelectedItem();
	}
	
	public void removeActionListener(EventListener<Event> listener) {
		listbox.removeActionListener(listener);
	}
	
	public void removeAllItems() {
		listbox.removeAllItems();
	}
	
	public void addItem(KeyNamePair pp) {
		listbox.addItem(pp);
	}
	
	public void setValue(Object value) {
		if (value instanceof KeyNamePair)
			this.setRawValue(((KeyNamePair) value).getName());
		
		listbox.setValue(value);
	}
	
	public void addActionListener(EventListener<Event> listener) {
		listbox.addActionListener(listener);
		this.addEventListener(Events.ON_CHANGING, listener);
		this.addEventListener(Events.ON_CHANGE, listener);
	}
}
