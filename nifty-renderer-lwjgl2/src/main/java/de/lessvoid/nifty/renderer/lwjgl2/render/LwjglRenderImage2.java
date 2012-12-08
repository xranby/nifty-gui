package de.lessvoid.nifty.renderer.lwjgl2.render;

import java.util.logging.Logger;

import de.lessvoid.coregl.CoreTexture2D;
import de.lessvoid.coregl.CoreTexture2D.ColorFormat;
import de.lessvoid.coregl.CoreTexture2D.ResizeFilter;
import de.lessvoid.coregl.CoreTextureAtlasGenerator;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.resourceloader.ResourceLoader;
import de.lessvoid.simpleimageloader.SimpleImageLoader;
import de.lessvoid.simpleimageloader.SimpleImageLoaderConfig;
import de.lessvoid.textureatlas.TextureAtlasGenerator.Result;

public class LwjglRenderImage2 implements RenderImage {
  private static Logger log = Logger.getLogger(LwjglRenderImage2.class.getName());
  private final int x;
  private final int y;
  private final int width;
  private final int height;

  public LwjglRenderImage2(final CoreTextureAtlasGenerator generator, final String name, final boolean filterParam, final ResourceLoader resourceLoader) {
    log.fine("loading image: " + name);

    SimpleImageLoader loader = new SimpleImageLoader();
    try {
      de.lessvoid.simpleimageloader.ImageData data = loader.load(name, resourceLoader.getResourceAsStream(name), new SimpleImageLoaderConfig().flipped().forceAlpha());
      CoreTexture2D texture = new CoreTexture2D(ColorFormat.RGBA, data.getWidth(), data.getHeight(), data.getData(), ResizeFilter.Linear);
      Result result = generator.addImage(texture, name, 5);
      x = result.getX();
      y = result.getY();
      width = data.getWidth();
      height = data.getHeight();
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void dispose() {
  }  

  public void bind() {
  }

  public int getX() {
    return x;
  }
  
  public int getY() {
    return y;
  }
}
