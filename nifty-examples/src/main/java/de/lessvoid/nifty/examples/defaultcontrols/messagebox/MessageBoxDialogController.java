package de.lessvoid.nifty.examples.defaultcontrols.messagebox;

import java.util.Properties;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.controls.MessageBox;
import de.lessvoid.nifty.controls.MessageBox.MessageType;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.xml.xpp3.Attributes;

public class MessageBoxDialogController implements Controller {
	
	private Nifty nifty;
	
	@Override
	public void bind(Nifty nifty, Screen screen, Element element,
			Properties parameter, Attributes controlDefinitionAttributes) {
		System.out.println("binding MessageBoxDialogController");
		this.nifty = nifty;
	}

	@Override
	public void init(Properties parameter,
			Attributes controlDefinitionAttributes) {
	}

	@Override
	public void onStartScreen() {
	}

	@Override
	public void onFocus(boolean getFocus) {
	}

	@Override
	public boolean inputEvent(NiftyInputEvent inputEvent) {
		return false;
	}

	public void displayMessageBoxOk() {
		System.out.println("displaying ok message box");
		// create the messagebox with just the ok button
		new MessageBox(nifty, MessageType.INFO, "Just showing an info messagebox with one button", "Ok").show();
	}

	public void displayMessageBoxOkCancel() {
		System.out.println("displaying ok/cancel message box");
		// create the messagebox with the ok and cancel button
		new MessageBox(nifty, MessageType.INFO, "Just showing an info messagebox with two buttons", new String[]{"Ok", "Cancel"}).show();
	}

}
