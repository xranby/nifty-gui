package de.lessvoid.nifty.renderer.lwjgl2.render;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import de.lessvoid.coregl.CoreRenderToTexture;
import de.lessvoid.coregl.CoreTexture2D;
import de.lessvoid.coregl.CoreTexture2D.ColorFormat;
import de.lessvoid.coregl.CoreTexture2D.ResizeFilter;
import de.lessvoid.coregl.CoreTextureAtlasGenerator;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.ImageData;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.ImageIOImageData;
import de.lessvoid.nifty.renderer.lwjgl2.render.io.TGAImageData;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.nifty.tools.resourceloader.NiftyResourceLoader;
import de.lessvoid.textureatlas.TextureAtlasGenerator.Result;

public class LwjglRenderImage2 implements RenderImage {
  private static Logger log = Logger.getLogger(LwjglRenderImage2.class.getName());
  private CoreRenderToTexture bla;

  public LwjglRenderImage2(final CoreTextureAtlasGenerator generator, final String name, final boolean filterParam, final NiftyResourceLoader resourceLoader) {
    try {
      log.fine("loading image: " + name);
      ImageData imageLoader;
      if (name.endsWith(".tga")) {
        imageLoader = new TGAImageData();
      } else {
        imageLoader = new ImageIOImageData();
      }
      ByteBuffer imageData = imageLoader.loadImage(resourceLoader.getResourceAsStream(name));
      imageData.rewind();
      CoreTexture2D texture = new CoreTexture2D(ColorFormat.RGBA, imageLoader.getWidth(), imageLoader.getHeight(), imageData, ResizeFilter.Linear);
      if (!generator.addImage(texture, name, 5)) {
        throw new Exception("unable to add image to texture atlas (" + name + ")");
      }
      bla = generator.getDone();
    } catch (Exception e) {
      e.printStackTrace();
      bla = null;
    }
  }

  public int getWidth() {
    return bla.getWidth();
  }

  public int getHeight() {
    return bla.getHeight();
  }

  public void dispose() {
  }  

  public void bind() {
    bla.bindTexture();
  }
}
