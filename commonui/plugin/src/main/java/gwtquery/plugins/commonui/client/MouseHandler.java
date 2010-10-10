/*
 * Copyright 2010 The gwtquery plugins team.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package gwtquery.plugins.commonui.client;

import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.GQuery;
import com.google.gwt.query.client.JSArray;
import com.google.gwt.query.client.plugins.Events;
import com.google.gwt.user.client.Event;

/**
 * Base class for all plug-in that need to handle some mouse interactions.
 * 
 * @author Julien Dramaix (julien.dramaix@gmail.com)
 * 
 */
public abstract class MouseHandler extends GQueryUi {

  private boolean preventClickEvent = false;
  private boolean mouseStarted = false;
  private Duration mouseUpDuration;
  private Event mouseDownEvent;
  private int mouseDownX;
  private int mouseDownY;
  private MouseOptions options;

  public MouseHandler(GQuery gq) {
    super(gq);
  }

  public MouseHandler(Element element) {
    super(element);
  }

  public MouseHandler(JSArray elements) {
    super(elements);
  }

  public MouseHandler(NodeList<Element> list) {
    super(list);
  }

  /**
   * This method initialize all needed handlers
   * 
   * @param options
   */
  protected void initMouseHandler(MouseOptions options) {
    this.options = options;

    for (final Element e : elements()) {

      $(e).as(Events.Events).bind(Event.ONMOUSEDOWN, getPluginName(),
          (Object) null, new Function() {
            @Override
            public boolean f(Event event) {
              return mouseDown(e, event);

            }
          }).bind(Event.ONCLICK, getPluginName(), (Object) null,
          new Function() {
            @Override
            public boolean f(Event event) {
              if (preventClickEvent) {
                preventClickEvent = false;
                event.stopPropagation();
                return false;
              }
              return true;
            }
          });
    }

  }

  protected void destroyMouseHandler() {
    as(Events.Events)
        .unbind(Event.ONMOUSEDOWN | Event.ONCLICK, getPluginName());
  }

  /**
   * Return a String identifying the plugin. This string is used as namespace
   * when we bind handlers.
   * 
   * @return
   */
  protected abstract String getPluginName();

  /**
   * Test if the mouse down event must be handled by the plugin or not.
   * 
   * 
   */
  protected boolean mouseCapture(Element draggable, Event event) {
    return true;
  }

  /**
   * Method called when the mouse is dragging
   * 
   * @param element
   * @param event
   * @return
   */
  protected abstract boolean mouseDrag(Element element, Event event);

  /**
   * Method called when the mouse is clicked and all conditions for starting the
   * plugin are met.
   * 
   * @param element
   * @param event
   * @return
   */
  protected abstract boolean mouseStart(Element element, Event event);

  /**
   * Method called when the mouse button is released
   * 
   * @param element
   * @param event
   * @return
   */
  protected abstract boolean mouseStop(Element element, Event event);

  private void bindOtherMouseEvent(final Element element) {

    $(document).as(Events.Events).bind(Event.ONMOUSEMOVE, getPluginName(),
        (Object) null, new Function() {
          @Override
          public boolean f(Event e) {
            mouseMove(element, e);
            return false;
          }
        }).bind(Event.ONMOUSEUP, getPluginName(), (Object) null,
        new Function() {
          @Override
          public boolean f(Event e) {
            mouseUp(element, e);
            return false;
          }
        });
  }

  private boolean delayConditionMet() {

    if (mouseUpDuration == null) {
      return false;
    }

    return options.getDelay() <= mouseUpDuration.elapsedMillis();
  }

  private boolean distanceConditionMet(Event event) {
    int neededDistance = options.getDistance();
    int xMouseDistance = Math.abs(mouseDownX - event.getClientX());
    int yMouseDistance = Math.abs(mouseDownY - event.getClientY());
    // in jQuery-ui we take the greater distance between x and y... not really
    // good !
    // int mouseDistance = Math.max(xMouseDistance, yMouseDistance);
    // use Pythagor theorem !!
    int mouseDistance = (int) Math.sqrt(xMouseDistance * xMouseDistance
        + yMouseDistance * yMouseDistance);
    return mouseDistance >= neededDistance;
  }

  private native boolean isEventAlreadyHandled(Event event)/*-{
    var result = event.mouseHandled ? event.mouseHandled : false;
    return result;
  }-*/;

  private native void markEventAsHandled(Event event)/*-{
    event.mouseHandled = true;
  }-*/;

  private boolean mouseDown(Element element, Event event) {

    // test if an other plugin handle the mouseStart
    if (isEventAlreadyHandled(event)) {
      return false;
    }
    if (mouseStarted) { // case where we missed a mouseup
      mouseUp(element, event);
    }

    // calculate all interesting variables
    reset(event);

    if (notHandleMouseDown(element, event)) {
      return true;
    }

    if (delayConditionMet() && distanceConditionMet(event)) {
      mouseStarted = mouseStart(element, event);
      if (!mouseStarted) {
        event.preventDefault();
        return true;
      }
    }

    bindOtherMouseEvent(element);

    event.preventDefault();

    markEventAsHandled(event);

    return true;
  }

  private boolean mouseMove(Element element, Event event) {
    if (mouseStarted) {
      event.preventDefault();
      return mouseDrag(element, event);
    }

    if (delayConditionMet() && distanceConditionMet(event)) {
      mouseStarted = mouseStart(element, mouseDownEvent);
      if (mouseStarted) {
        mouseDrag(element, event);
      } else {
        mouseUp(element, event);
      }
    }

    return !mouseStarted;
  }

  private boolean mouseUp(Element element, Event event) {
    unbindOtherMouseEvent();
    if (mouseStarted) {
      mouseStarted = false;
      preventClickEvent = (event.getCurrentEventTarget() == mouseDownEvent
          .getCurrentEventTarget());
      mouseStop(element, event);
    }
    return false;

  }

  private boolean notHandleMouseDown(Element element, Event mouseDownEvent) {
    boolean isNotBoutonLeft = mouseDownEvent.getButton() != NativeEvent.BUTTON_LEFT;
    Element eventTarget = mouseDownEvent.getEventTarget().cast();

    boolean isElementCancel = $(eventTarget).parents().add($(eventTarget))
        .filter(options.getCancel()).length() > 0;

    return isNotBoutonLeft || isElementCancel
        || !mouseCapture(element, mouseDownEvent);

  }

  private void reset(Event mouseDownEvent) {
    this.mouseDownEvent = mouseDownEvent;
    this.mouseDownX = mouseDownEvent.getClientX();
    this.mouseDownY = mouseDownEvent.getClientY();
    this.mouseUpDuration = new Duration();
  }

  private void unbindOtherMouseEvent() {
    // TODO waiting correction of issue 48
    // $(document).as(Events.Events).unbind((Event.ONMOUSEUP |
    // Event.ONMOUSEMOVE), getPluginName());
    $(document).as(Events.Events).unbind(Event.ONMOUSEUP, getPluginName())
        .unbind(Event.ONMOUSEMOVE, getPluginName());
  }

}