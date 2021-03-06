package de.lessvoid.nifty.controls.nullobjects;

import de.lessvoid.nifty.controls.Scrollbar;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.tools.SizeValue;

public class ScrollbarNull implements Scrollbar {

  @Override
  public Element getElement() {
    return null;
  }

  @Override
  public String getId() {
    return "ScrollbarNull";
  }

  @Override
  public void setId(final String id) {
  }

  @Override
  public int getWidth() {
    return 0;
  }

  @Override
  public void setWidth(final SizeValue width) {
  }

  @Override
  public int getHeight() {
    return 0;
  }

  @Override
  public void setHeight(final SizeValue height) {
  }

  @Override
  public String getStyle() {
    return null;
  }

  @Override
  public void setStyle(final String style) {
  }

  @Override
  public void enable() {
  }

  @Override
  public void disable() {
  }

  @Override
  public void setEnabled(final boolean enabled) {
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setup(final float value, final float worldMax, final float worldPageSize, final float buttonStepSize, final float pageStepSize) {
  }

  @Override
  public void setValue(final float value) {
  }

  @Override
  public float getValue() {
    return 0;
  }

  @Override
  public void setWorldMax(final float worldMax) {
  }

  @Override
  public float getWorldMax() {
    return 0;
  }

  @Override
  public void setWorldPageSize(final float worldPageSize) {
  }

  @Override
  public float getWorldPageSize() {
    return 0;
  }

  @Override
  public void setButtonStepSize(final float stepSize) {
  }

  @Override
  public float getButtonStepSize() {
    return 0;
  }

  @Override
  public void setPageStepSize(final float stepSize) {
  }

  @Override
  public float getPageStepSize() {
    return 0;
  }

  @Override
  public void setFocus() {
  }

  @Override
  public void setFocusable(final boolean focusable) {
  }

  @Override
  public boolean hasFocus() {
    return false;
  }

  @Override
  public void layoutCallback() {
  }

  @Override
  public boolean isBound() {
    return false;
  }
}
