package com.sb.framework.http.image;

import java.io.File;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.widget.ImageView;

/**
 * 统一管理所有image的下载，存储和缓存
 * 
 * ImageMgmr.configLocalPath(从服务器现在过来放在哪儿) //这个会附带创建这个目录
		.configImageNameGenerator(new NameGemerator(){
				public void generateNewName(String url){
					
				}
			}) //
	.configImageThreadCount(2)
 * 
 * @author seven
 *
 */
public class ImageMgmr {

	public static String localCachePath = ImageFileUtils.getAppHomeDirAtSDCard(); //给个默认值
	public static NameGenerator nameGenerator = null; //默认使用服务器的文件名
	public static int threadCount = 2;
	public static Downloader downloader = null;
	
	public static boolean debug = true;
	public static void log(String msg){
		System.out.println(msg);
	}
	
	/**
	 * 以/结尾
	 * @param path
	 */
	public static void configLocalCachePath(String path){
		if(!TextUtils.isEmpty(path)) localCachePath = path;
		if(!localCachePath.endsWith("/")){
			localCachePath += "/";
		}
		File f = new File(localCachePath);
		if(!f.exists() || !f.isDirectory()){
			f.mkdirs();
		}
	}
	
	public static void configImageNameGenerator(NameGenerator nameGenerator){
		ImageMgmr.nameGenerator = nameGenerator;
	}
	
	public interface NameGenerator{
		String generateNewName(String url, String fileName);
	}
	public static void configImageThreadCount(int threadCount){
		ImageMgmr.threadCount = threadCount;
	}
	
	public interface Downloader{
		/**
		 * 从url下载文件，存在本地outpath下（包含文件名），并要调用callback中的四个函数，而且要根据useOldFile决定是否使用本地缓存的文件
		 * 
		 * if(useOldFile){
		 * 		if(outpath存在且正常）{
		 * 			callback.onOK(outpath);
		 * 			return;
		 * 		}
		 * 		
		 * }
		 * 
		 * @param url
		 * @param outpath
		 * @param callback
		 * @param useOldFile
		 */
		void download(String url, String outpath, OnImageDownloadCallback callback, boolean useOldFile);
	}
	
	public static ImageDownloaderAndShower imageShower;
	
	/**
	 * callback对Volly无效
	 * useOldFile对Volly无效，Volly总是从上下文的cacheDir中的volly拿出缓存
	 * @author seven
	 *
	 */
	public interface ImageDownloaderAndShower{
		public void showImage(final ImageView imageview, 
				String url, 
				final int loadingImage, final int errorImage, 
				final OnImageDownloadCallback callback, 
				final int w, final int h
				//final boolean useOldFile, 
				//final boolean useMemoryCache
				);
	}
	
	public static void configDownloaderAndShower(ImageDownloaderAndShower imageShower){
		ImageMgmr.imageShower = imageShower;
	}
	
	public static void configDownloader(Downloader downloader){
		ImageMgmr.downloader = downloader;
	}
	
	//-----------------------------------------

	/**
	 * 下载图片
	 * @param url
	 * @param callback
	 * @param useOldFile  是否使用sd卡缓存
	 */
	public static void download(String url, OnImageDownloadCallback callback, boolean useOldFile)
	{
		if(downloader == null){
			ImageReaper.downloadImage(url, true, callback, useOldFile);
		}else{
			downloader.download(url, ImageFileUtils.getImagePath(url), callback, useOldFile);
		}
		
	}
	
	/**
	 * ImageView显示网络图片
	 * @param imageview
	 * @param url
	 * @param loadingImage  下载过程中显示的图片
	 * @param errorImage    下载错误时显示的图片
	 * @param callback
	 * @param w   显示的宽度，像素，0则对于Volly来讲，是根据ImageView大小来缩放，不多用一点内存，其他实现方式不知道，可能是解析整个图片，不论多大
	 * @param h	  显示的高度，像素
	 * @param useOldFile  是否使用sd卡缓存下载好的文件
	 * @param useMemoryCache  是否使用内存缓存解析好的Bitmap
	 */
	public static void showImage(final ImageView imageview, String url, final int loadingImage, final int errorImage, final OnImageDownloadCallback callback, final int w, final int h, final boolean useOldFile, final boolean useMemoryCache){
		if(imageview == null) return;
		if(ImageMgmr.imageShower != null){
			System.out.println("use第三方图片加载了");
			ImageMgmr.imageShower.showImage(imageview, url, loadingImage, errorImage, callback, w, h);//, useOldFile, useMemoryCache);
			return;
		}
		OnImageDownloadCallback localCallback = new OnImageDownloadCallback() {
			
			@Override
			public void onStart() {
				if(loadingImage != 0) imageview.setImageResource(loadingImage);
				if(callback != null) callback.onStart();
			}
			
			@Override
			public void onOK(String path) {
				Bitmap bm = ImageDecoder.decodeBitmap(path, w, h, useMemoryCache);
				imageview.setImageBitmap(bm);
				if(callback != null) callback.onOK(path);
			}
			
			@Override
			public void onLoading(int progress, int total) {
				if(callback != null) callback.onLoading(progress, total);
			}
			
			@Override
			public void onFuck(String errorInfo) {
				if(errorImage != 0) imageview.setImageResource(errorImage);
				if(callback != null) callback.onFuck(errorInfo);
			}
		};
		
		if(downloader == null){
			ImageReaper.downloadImage(url, true, localCallback, useOldFile);
		}else{
			downloader.download(url, ImageFileUtils.getImagePath(url), localCallback, useOldFile);
		}
	}
	
	
	//-----------------------------------------
	
	public static Bitmap loadImageFromServer(String imageUrl, int reqWidth, int reqHeight) {
		
		/**
		 * 此操作封装了本地缓存：如果imageUrl对应的图片已经下载过，就不需要再下载
		 */
		String path = ImageReaper.downloadImage(imageUrl, false, null, true);
		
		/**
		 * 此操作封装了Lru缓存：如果对应path的图片已经解析过，就不需要再解析
		 */
		return ImageDecoder.decodeBitmap(path, reqWidth, reqHeight, true);
		
	}
	//------------------------------
	public static void cleanCacheInSD(){
		ImageFileUtils.deleteFile();
	}
}
