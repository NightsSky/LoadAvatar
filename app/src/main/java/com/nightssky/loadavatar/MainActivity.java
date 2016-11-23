package com.nightssky.loadavatar;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.nightssky.loadavatar.Utils.BitmapUtils;
import com.nightssky.loadavatar.Utils.FileUtil;
import com.nightssky.loadavatar.Utils.NetUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;




public class MainActivity extends Activity implements OnClickListener {
	private Context mContext;
	private CircleImg avatarImg;// 头像图片
	private Button loginBtn;// 页面的登录按钮
	private SelectPicPopupWindow menuWindow; // 自定义的头像编辑弹出框
	// 上传服务器的路径【一般不硬编码到程序中】
	private String imgUrl = "";
	private static final String IMAGE_FILE_NAME = "avatarImage.jpg";// 头像文件名称
	private String urlpath;			// 图片本地路径
	private String resultStr = "";	// 服务端返回结果集
	private static ProgressDialog pd;// 等待进度圈
	private static final int REQUESTCODE_PICK = 0;		// 相册选图标记
	private static final int REQUESTCODE_TAKE = 1;		// 相机拍照标记
	private static final int REQUESTCODE_CUTTING = 2;	// 图片裁切标记
	private Bitmap mBitmap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mContext = MainActivity.this;

		initView();
	}

	/**
	 * 初始化页面控件
	 */
	private void initView() {
		avatarImg = (CircleImg) findViewById(R.id.avatarImg);
		avatarImg.setOnClickListener(this);
		initData();
	}

	private void initData() {
		try {
			File file = new File(Environment.getExternalStorageDirectory() + "/AHead/" + "temphead.jpg");
			if (file.isFile()) {
				BitmapUtils.setSize(300, 300);
				mBitmap = BitmapUtils.getBitmap(Environment.getExternalStorageDirectory() + "/AHead/" + "temphead.jpg");
				avatarImg.setImageBitmap(mBitmap);
			}
		} catch (Exception e) {
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.avatarImg:// 更换头像点击事件
				menuWindow = new SelectPicPopupWindow(mContext, itemsOnClick);
				menuWindow.showAtLocation(findViewById(R.id.mainLayout),
						Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, 0, 0);
				break;

			default:
				break;
		}
	}

	//为弹出窗口实现监听类
	private OnClickListener itemsOnClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			menuWindow.dismiss();
			switch (v.getId()) {
				// 拍照
				case R.id.takePhotoBtn:
					Intent takeIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					//下面这句指定调用相机拍照后的照片存储的路径
					takeIntent.putExtra(MediaStore.EXTRA_OUTPUT,
							Uri.fromFile(new File(Environment.getExternalStorageDirectory(), IMAGE_FILE_NAME)));
					startActivityForResult(takeIntent, REQUESTCODE_TAKE);
					break;
				// 相册选择图片
				case R.id.pickPhotoBtn:
					Intent pickIntent = new Intent(Intent.ACTION_PICK, null);
					// 如果朋友们要限制上传到服务器的图片类型时可以直接写如："image/jpeg 、 image/png等的类型"
					pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
					startActivityForResult(pickIntent, REQUESTCODE_PICK);
					break;
				default:
					break;
			}
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
			case REQUESTCODE_PICK:// 直接从相册获取
				try {
					startPhotoZoom(data.getData());
				} catch (NullPointerException e) {
					e.printStackTrace();// 用户点击取消操作
				}
				break;
			case REQUESTCODE_TAKE:// 调用相机拍照
				File temp = new File(Environment.getExternalStorageDirectory() + "/" + IMAGE_FILE_NAME);
				startPhotoZoom(Uri.fromFile(temp));
				break;
			case REQUESTCODE_CUTTING:// 取得裁剪后的图片
				if (data != null) {
					setPicToView(data);
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * 裁剪图片方法实现
	 * @param uri
	 */
	public void startPhotoZoom(Uri uri) {
		Intent intent = new Intent("com.android.camera.action.CROP");
		intent.setDataAndType(uri, "image/*");
		// crop=true是设置在开启的Intent中设置显示的VIEW可裁剪
		intent.putExtra("crop", "true");
		// aspectX aspectY 是宽高的比例
		intent.putExtra("aspectX", 1);
		intent.putExtra("aspectY", 1);
		// outputX outputY 是裁剪图片宽高
		intent.putExtra("outputX", 300);
		intent.putExtra("outputY", 300);
		intent.putExtra("return-data", true);
		startActivityForResult(intent, REQUESTCODE_CUTTING);
	}

	/**
	 * 保存裁剪之后的图片数据
	 * @param picdata
	 */
	private void setPicToView(Intent picdata) {
		Bundle extras = picdata.getExtras();
		if (extras != null) {
			// 取得SDCard图片路径做显示
			Bitmap photo = extras.getParcelable("data");
			Drawable drawable = new BitmapDrawable(null, photo);
			urlpath = FileUtil.saveFile(mContext, "temphead.jpg", photo);
			avatarImg.setImageDrawable(drawable);

			// 新线程后台上传服务端
			pd = ProgressDialog.show(mContext, null, "正在上传图片，请稍候...");
			new Thread(uploadImageRunnable).start();
		}
	}

	/**
	 * 使用HttpUrlConnection模拟post表单进行文件
	 * 上传平时很少使用，比较麻烦
	 * 原理是： 分析文件上传的数据格式，然后根据格式构造相应的发送给服务器的字符串。
	 */
	Runnable uploadImageRunnable = new Runnable() {
		@Override
		public void run() {

			if(TextUtils.isEmpty(imgUrl)){
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						pd.dismiss();
						Toast.makeText(mContext, "还没有设置上传服务器的路径！", Toast.LENGTH_SHORT).show();
					}
				});

				return;
			}

			Map<String, String> textParams = new HashMap<String, String>();
			Map<String, File> fileparams = new HashMap<String, File>();

			try {
				// 创建一个URL对象
				URL url = new URL(imgUrl);
				textParams = new HashMap<String, String>();
				fileparams = new HashMap<String, File>();
				// 要上传的图片文件
				File file = new File(urlpath);
				fileparams.put("image", file);
				// 利用HttpURLConnection对象从网络中获取网页数据
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				// 设置连接超时（记得设置连接超时,如果网络不好,Android系统在超过默认时间会收回资源中断操作）
				conn.setConnectTimeout(5000);
				// 设置允许输出（发送POST请求必须设置允许输出）
				conn.setDoOutput(true);
				// 设置使用POST的方式发送
				conn.setRequestMethod("POST");
				// 设置不使用缓存（容易出现问题）
				conn.setUseCaches(false);
				conn.setRequestProperty("Charset", "UTF-8");//设置编码
				// 在开始用HttpURLConnection对象的setRequestProperty()设置,就是生成HTML文件头
				conn.setRequestProperty("ser-Agent", "Fiddler");
				// 设置contentType
				conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + NetUtil.BOUNDARY);
				OutputStream os = conn.getOutputStream();
				DataOutputStream ds = new DataOutputStream(os);
				NetUtil.writeStringParams(textParams, ds);
				NetUtil.writeFileParams(fileparams, ds);
				NetUtil.paramsEnd(ds);
				// 对文件流操作完,要记得及时关闭
				os.close();
				// 服务器返回的响应吗
				int code = conn.getResponseCode(); // 从Internet获取网页,发送请求,将网页以流的形式读回来
				// 对响应码进行判断
				if (code == 200) {// 返回的响应码200,是成功
					// 得到网络返回的输入流
					InputStream is = conn.getInputStream();
					resultStr = NetUtil.readString(is);
				} else {
					pd.dismiss();
					Toast.makeText(mContext, "请求URL失败！", Toast.LENGTH_SHORT).show();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			handler.sendEmptyMessage(0);// 执行耗时的方法之后发送消给handler
		}
	};

	Handler handler = new Handler(new Handler.Callback() {

		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
				case 0:
					pd.dismiss();

					try {
						// 返回数据示例，根据需求和后台数据灵活处理
						// {"status":"1","statusMessage":"上传成功","imageUrl":"http://120.24.219.49/726287_temphead.jpg"}
						JSONObject jsonObject = new JSONObject(resultStr);

						// 服务端以字符串“1”作为操作成功标记
						if (jsonObject.optString("status").equals("1")) {
							BitmapFactory.Options option = new BitmapFactory.Options();
							// 压缩图片:表示缩略图大小为原始图片大小的几分之一，1为原图，3为三分之一
							option.inSampleSize = 1;

							// 服务端返回的JsonObject对象中提取到图片的网络URL路径
							String imageUrl = jsonObject.optString("imageUrl");
							Toast.makeText(mContext, imageUrl, Toast.LENGTH_SHORT).show();
						}else{
							Toast.makeText(mContext, jsonObject.optString("statusMessage"), Toast.LENGTH_SHORT).show();
						}

					} catch (JSONException e) {
						e.printStackTrace();
					}

					break;

				default:
					break;
			}
			return false;
		}
	});
}
