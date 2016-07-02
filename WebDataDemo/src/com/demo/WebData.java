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
			//����GetAccountData����
			jArr = api.getArray("GetAccountData");
			//�ӷ��ص�Account Array��ȡ����һ������
			jobj = jArr.getJSONObject(0);
			
			GsonBuilder gsonb = new GsonBuilder();
			//Json�е����ڱ��﷽ʽû�а취ֱ��ת�������ǵ�Date����, �����Ҫ����ע��һ��Date�ķ����л���.
			DateDeserializer ds = new DateDeserializer();
			//��GsonBuilder��������ָ��Date���͵ķ����л�����
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