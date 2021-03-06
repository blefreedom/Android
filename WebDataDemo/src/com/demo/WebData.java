package com.demo;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class WebData extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		getJsonData();
	}

	public void getJsonData() {
		JsonDataGetApi api = new JsonDataGetApi();
		JSONArray jArr;
		JSONObject jobj;
		try {
			//调用GetAccountData方法
			jArr = api.getArray("GetAccountData");
			//从返回的Account Array中取出第一个数据
			jobj = jArr.getJSONObject(0);
			
			GsonBuilder gsonb = new GsonBuilder();
			//Json中的日期表达方式没有办法直接转换成我们的Date类型, 因此需要单独注册一个Date的反序列化类.
			DateDeserializer ds = new DateDeserializer();
			//给GsonBuilder方法单独指定Date类型的反序列化方法
			gsonb.registerTypeAdapter(Date.class, ds);
			
			Gson gson = gsonb.create();

			Account account = gson.fromJson(jobj.toString(), Account.class);

			Log.d("LOG_CAT", jobj.toString());
			((TextView) findViewById(R.id.Name)).setText(account.Name);
			((TextView) findViewById(R.id.Age)).setText(String.valueOf(account.Age));
			((TextView) findViewById(R.id.Birthday)).setText(account.Birthday
					.toGMTString());
			((TextView) findViewById(R.id.Address)).setText(account.Address);

		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), e.getMessage(),
					Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}
}
