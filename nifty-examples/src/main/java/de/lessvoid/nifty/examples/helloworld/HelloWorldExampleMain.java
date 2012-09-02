package de.lessvoid.nifty.examples.helloworld;

import de.lessvoid.coregl.CoreLwjglSetup;
import de.lessvoid.coregl.CoreLwjglSetup.RenderLoopCallback;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.examples.LoggerShortFormat;
import de.lessvoid.nifty.examples.LwjglInitHelper;
import de.lessvoid.nifty.renderer.lwjgl.render.LwjglRenderDevice;
import de.lessvoid.nifty.renderer.lwjgl2.render.LwjglRenderDevice2;
import de.lessvoid.nifty.sound.openal.OpenALSoundDevice;
import de.lessvoid.nifty.spi.time.impl.AccurateTimeProvider;

/**
 * The Nifty Hello World.
 * @author void
 */
public final class HelloWorldExampleMain {

  /**
   * Prevent instantiation of this class.
   */
  private HelloWorldExampleMain() {
  }

  /**
   * Main method.
   * @param args arguments
   */
  public static void main(final String[] args) throws Exception {
    runStandard();
    //runCore();
  }

  private static void runStandard() {
    if (!LwjglInitHelper.initSubSystems("Nifty Hello World")) {
      System.exit(0);
    }

    // create nifty
    Nifty nifty = new Nifty(
        new LwjglRenderDevice(true),
        new OpenALSoundDevice(),
        LwjglInitHelper.getInputSystem(),
        new AccurateTimeProvider());

    final HelloWorldStartScreen screen = new HelloWorldStartScreen();
    nifty.registerScreenController(screen);

    screen.prepareStart(nifty);

    nifty.fromXml("src/main/resources/helloworld/helloworld.xml", "start");

    LwjglInitHelper.renderLoop(nifty, null);
    LwjglInitHelper.destroy();
  }

  private static void runCore() throws Exception {
    LoggerShortFormat.intialize();

    CoreLwjglSetup setup = new CoreLwjglSetup();
    setup.initialize("Nifty Hello World", 1024, 768);

    if (!LwjglInitHelper.initInput()) {
      throw new Exception("Failed to init Input");
    }

    // create nifty
    final Nifty nifty = new Nifty(
        new LwjglRenderDevice2(true),
        new OpenALSoundDevice(),
        LwjglInitHelper.getInputSystem(),
        new AccurateTimeProvider());

    final HelloWorldStartScreen screen = new HelloWorldStartScreen();
    nifty.registerScreenController(screen);

    screen.prepareStart(nifty);

    nifty.fromXml("src/main/resources/helloworld/helloworld.xml", "start");

    setup.renderLoop(new RenderLoopCallback() {
      
      @Override
      public boolean render(float deltaTime) {
        boolean done = nifty.update();
        nifty.render(true);
        return done;
      }
    });

    LwjglInitHelper.renderLoop(nifty, null);
    LwjglInitHelper.destroy();
  }
}
