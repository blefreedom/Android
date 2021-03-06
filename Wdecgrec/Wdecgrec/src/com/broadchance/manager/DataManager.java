package com.broadchance.manager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.broadchance.entity.FileType;
import com.broadchance.entity.UIUserInfoLogin;
import com.broadchance.entity.UploadFile;
import com.broadchance.entity.UploadFileStatus;
import com.broadchance.entity.UserStatus;
import com.broadchance.utils.ConstantConfig;
import com.broadchance.utils.DBHelper;
import com.broadchance.utils.LogUtil;

public class DataManager {
	private final static String TAG = DataManager.class.getSimpleName();
	private static UIUserInfoLogin USER;
	private static Object objUploadFileLock = new Object();

	public static boolean deleteUserPwd(String userName) {
		DBHelper dbHelper = DBHelper.getInstance();
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		db.execSQL("update " + DBHelper.TBL_USER
				+ "  set pwd=null where user_name=" + userName);
		db.close();
		return true;
	}

	/**
	 * 保存帐号数据
	 * 
	 * @param user_name
	 * @param pwd
	 * @return
	 */
	public static boolean saveUser(UIUserInfoLogin userInfo, String pwd) {
		DBHelper dbHelper = DBHelper.getInstance();
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		try {
			USER = userInfo;
			db.beginTransaction();
			db.execSQL("update " + DBHelper.TBL_USER + "  set status="
					+ UserStatus.None.getValue());
			boolean isExist = false;
			String sql = "select 1 from " + DBHelper.TBL_USER
					+ " where user_name=?";
			String[] selectionArgs = new String[] { userInfo.getLoginName() };
			Cursor cursor = db.rawQuery(sql, selectionArgs);
			for (cursor.moveToFirst(); !(cursor.isAfterLast()); cursor
					.moveToNext()) {
				isExist = true;
				break;
			}
			cursor.close();
			if (isExist) {
				db.execSQL(
						"update "
								+ DBHelper.TBL_USER
								+ "  set status=?,pwd=?,token=? where user_name=?",
						new Object[] { UserStatus.Login.getValue(), pwd,
								userInfo.getAccess_token(),
								userInfo.getLoginName() });
			} else {
				db.execSQL(
						"insert into "
								+ DBHelper.TBL_USER
								+ " (user_name , pwd , userid ,token,status) values (?,?,?,?,?) ",
						new Object[] { userInfo.getLoginName(), pwd,
								userInfo.getUserID(),
								userInfo.getAccess_token(),
								UserStatus.Login.getValue() });
			}
			db.setTransactionSuccessful();
			return true;
		} catch (Exception e) {
			LogUtil.e(TAG, e);
		} finally {
			db.endTransaction();
			db.close();
		}
		return false;
	}

	/**
	 * 根据账号获取密码
	 * 
	 * @param user_name
	 * @return
	 */
	public static String getUserPwd() {
		String pwd = null;
		getUserInfo();
		if (USER == null)
			return null;
		String user_name = USER.getLoginName();
		DBHelper dbHelper = DBHelper.getInstance();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		String sql = "select user_name,pwd from " + DBHelper.TBL_USER
				+ " where user_name=?";
		String[] selectionArgs = new String[] { user_name };
		// UIUserInfoRegist user = new UIUserInfoRegist();
		Cursor cursor = db.rawQuery(sql, selectionArgs);
		for (cursor.moveToFirst(); !(cursor.isAfterLast()); cursor.moveToNext()) {
			// user.setLoginName(cursor.getString(cursor
			// .getColumnIndex("user_name")));
			// user.setPassword(cursor.getString(cursor.getColumnIndex("pwd")));
			pwd = cursor.getString(cursor.getColumnIndex("pwd"));
		}
		cursor.close();
		db.close();
		return pwd;
	}

	public static UIUserInfoLogin getUserInfo() {
		if (USER != null)
			return USER;
		DBHelper dbHelper = DBHelper.getInstance();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		String sql = "select user_name , pwd , userid ,token from "
				+ DBHelper.TBL_USER + " where status=?";
		String[] selectionArgs = new String[] { UserStatus.Login.getValue()
				+ "" };
		UIUserInfoLogin user = new UIUserInfoLogin();
		Cursor cursor = db.rawQuery(sql, selectionArgs);
		for (cursor.moveToFirst(); !(cursor.isAfterLast()); cursor.moveToNext()) {
			user.setLoginName(cursor.getString(cursor
					.getColumnIndex("user_name")));
			user.setUserID(cursor.getString(cursor.getColumnIndex("userid")));
			user.setAccess_token(cursor.getString(cursor
					.getColumnIndex("token")));
		}
		cursor.close();
		db.close();
		USER = (user.getUserID() != null && user.getUserID().trim().length() > 0) ? user
				: null;
		return USER;
	}

	/**
	 * 保存上传队列表
	 * 
	 * @param fileName
	 *            mii201601021525234(frametypename+yyyyMMddHHmmssSSSZ)
	 * @param path
	 *            文件的绝对路径
	 * @param status
	 *            0数据未做处理1正在上传2上传成功3上传失败
	 * @param uploadtimes
	 *            上传重试次数
	 * @param dataBeginTime
	 *            文件中第一组数据点的从设备的接收到时间
	 * @param dataEndTime
	 *            文件中最后一组数据点的从设备的接收到时间
	 * @param type
	 *            是否补传文件
	 * @return
	 */
	public static boolean saveUploadFile(String fileName, String path,
			Date dataBeginTime, Date dataEndTime, FileType type) {
		synchronized (objUploadFileLock) {
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(
						ConstantConfig.DATA_DATE_FORMAT);
				DBHelper dbHelper = DBHelper.getInstance();
				SQLiteDatabase db = dbHelper.getWritableDatabase();
				// db.execSQL(
				// "insert into "
				// + DBHelper.TBL_UPLOAD
				// +
				// " (file_name,user_id,path,status,uploadtimes,data_begintime,data_endtime,creation_date) values (?,?,?,?,?,?) ",
				// new Object[] { fileName, ConstantConfig.USER.getUserID(),
				// path, UploadFileStatus.UnDeal.getValue(), 0,
				// sdf.format(dataBeginTime), sdf.format(dataEndTime),
				// sdf.format(new Date()) });
				UIUserInfoLogin user = DataManager.getUserInfo();
				if (user == null) {
					LogUtil.d(TAG, "saveUploadFile " + "用户数据不存在");
					return false;
				}
				ContentValues cValue = new ContentValues();
				cValue.put("file_name", fileName);
				cValue.put("user_id", user.getUserID());
				cValue.put("path", path);
				cValue.put("status", UploadFileStatus.UnDeal.getValue());
				cValue.put("uploadtimes", 0);
				cValue.put("data_begintime", sdf.format(dataBeginTime));
				cValue.put("data_endtime", sdf.format(dataEndTime));
				cValue.put("creation_date", sdf.format(new Date()));
				cValue.put("upload_date", sdf.format(new Date()));
				cValue.put("filetype", type.getValue());
				long rowid = db.insert(DBHelper.TBL_UPLOAD, null, cValue);
				db.close();
				return rowid != -1;
			} catch (Exception e) {
				LogUtil.e(TAG, e);
			}
		}
		return false;
	}

	/**
	 * 根据文件名删除文件
	 * 
	 * @param fileName
	 * @return
	 */
	public static boolean deleteUploadFile(String fileName) {
		synchronized (objUploadFileLock) {
			try {
				DBHelper dbHelper = DBHelper.getInstance();
				SQLiteDatabase db = dbHelper.getWritableDatabase();
				// db.execSQL("delete from  " + DBHelper.TBL_UPLOAD
				// + " where file_name=?  ", new Object[] { fileName });
				String whereClause = "file_name=?";
				String[] whereArgs = { fileName };
				int rows = db.delete(DBHelper.TBL_UPLOAD, whereClause,
						whereArgs);
				db.close();
				return rows == 1;
			} catch (Exception e) {
				LogUtil.e(TAG, e);
			}
		}
		return false;
	}

	/**
	 * 更新上传次数
	 * 
	 * @param uploadFiles
	 * @return
	 */
	public static boolean updateUploadFileTimes(List<UploadFile> uploadFiles) {
		synchronized (objUploadFileLock) {
			DBHelper dbHelper = DBHelper.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat(
					ConstantConfig.DATA_DATE_FORMAT);
			SQLiteDatabase db = dbHelper.getWritableDatabase();
			// 开始事务
			db.beginTransaction();
			try {
				for (UploadFile file : uploadFiles) {
					db.execSQL(
							"update  "
									+ DBHelper.TBL_UPLOAD
									+ " set uploadtimes=uploadtimes+1,upload_date=?  where file_name=?  ",
							new Object[] { sdf.format(new Date()),
									file.getFileName() });
				}
				// 设置事务成功完成
				db.setTransactionSuccessful();
			} finally {
				// 结束事务
				db.endTransaction();
				db.close();
			}
		}
		return false;
	}

	/**
	 * 更新文件的上传状态
	 * 
	 * @param fileName
	 * @param status
	 *            0数据未做处理1正在上传2上传成功3上传失败
	 * @return
	 */
	public static boolean updateUploadFileStatus(List<UploadFile> uploadFiles,
			UploadFileStatus status) {
		synchronized (objUploadFileLock) {
			DBHelper dbHelper = DBHelper.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat(
					ConstantConfig.DATA_DATE_FORMAT);
			SQLiteDatabase db = dbHelper.getWritableDatabase();
			// 开始事务
			db.beginTransaction();
			try {
				for (UploadFile file : uploadFiles) {
					ContentValues values = new ContentValues();
					values.put("status", status.getValue());
					values.put("upload_date", sdf.format(new Date()));
					String whereClause = "file_name=?";
					String[] whereArgs = { file.getFileName() };
					int rows = db.update(DBHelper.TBL_UPLOAD, values,
							whereClause, whereArgs);
					if (rows < 1) {
						return false;
					}
				}
				// 设置事务成功完成
				db.setTransactionSuccessful();
				return true;
			} finally {
				// 结束事务
				db.endTransaction();
				db.close();
			}
		}
		// try {
		// DBHelper dbHelper = DBHelper.getInstance();
		// SQLiteDatabase db = dbHelper.getWritableDatabase();
		// // db.execSQL("update  " + DBHelper.TBL_UPLOAD
		// // + " set status=?  where file_name=?  ", new Object[] {
		// // status.getValue(), fileName });
		//
		// ContentValues values = new ContentValues();
		// values.put("status", status.getValue());
		// String whereClause = "file_name=?";
		// String[] whereArgs = { fileName };
		// int rows = db.update(DBHelper.TBL_UPLOAD, values, whereClause,
		// whereArgs);
		// db.close();
		// return rows == 1;
		// } catch (Exception e) {
		// LogUtil.e(TAG, e);
		// }
	}

	public static boolean isUploadFileExist(String fileName) {
		DBHelper dbHelper = DBHelper.getInstance();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		Cursor cursor = null;
		try {
			String sql = "select file_name  from " + DBHelper.TBL_UPLOAD
					+ " where file_name=?";
			String[] selectionArgs = new String[] { fileName };
			cursor = db.rawQuery(sql, selectionArgs);
			boolean isExist = false;
			for (cursor.moveToFirst(); !(cursor.isAfterLast()); cursor
					.moveToNext()) {
				isExist = true;
				break;
			}
			return isExist;
		} catch (Exception e) {
			if (ConstantConfig.Debug) {
				LogUtil.e(TAG, e);
			}
		} finally {
			if (cursor != null)
				cursor.close();
			db.close();
		}
		return false;
	}

	/**
	 * 获取待上传的列表
	 * 
	 * @param date
	 *            抓取该时间之后数据
	 * @return
	 */
	public static List<UploadFile> getUploadFile(Date date, int limit) {
		try {
			UIUserInfoLogin user = DataManager.getUserInfo();
			if (user == null) {
				LogUtil.d(TAG, "getUploadFile " + "用户数据不存在");
				return null;
			}
			SimpleDateFormat sdf = new SimpleDateFormat(
					ConstantConfig.DATA_DATE_FORMAT);
			List<UploadFile> files = new ArrayList<UploadFile>();
			DBHelper dbHelper = DBHelper.getInstance();
			SQLiteDatabase db = dbHelper.getReadableDatabase();
			// 查询给定时间之后且数据未上传或者上传失败或者上传超时(默认时间2min)发生异常导致数据状态未回写的数据，抓取指定条数
			int timeoutData = 2;
			String sql = "select file_name,path,uploadtimes,data_begintime,data_endtime,upload_date,status,filetype  from "
					+ DBHelper.TBL_UPLOAD
					+ " where creation_date>=? and (status=? or status=? or upload_date<?) and user_id=? "
					+ (limit > 0 ? "limit ?" : "")
					+ "  order by creation_date desc  ";
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.MINUTE, -timeoutData);
			String[] selectionArgs = null;
			if (limit > 0) {
				selectionArgs = new String[] { sdf.format(date),
						UploadFileStatus.UnDeal.getValue() + "",
						UploadFileStatus.UploadFailed.getValue() + "",
						sdf.format(calendar.getTime()), user.getUserID(),
						limit + "" };
			} else {
				selectionArgs = new String[] { sdf.format(date),
						UploadFileStatus.UnDeal.getValue() + "",
						UploadFileStatus.UploadFailed.getValue() + "",
						sdf.format(calendar.getTime()), user.getUserID() };
			}

			UploadFile file = null;
			Cursor cursor = db.rawQuery(sql, selectionArgs);
			for (cursor.moveToFirst(); !(cursor.isAfterLast()); cursor
					.moveToNext()) {
				file = new UploadFile();
				file.setFileName(cursor.getString(cursor
						.getColumnIndex("file_name")));
				file.setPath(cursor.getString(cursor.getColumnIndex("path")));
				file.setUploadtimes(cursor.getInt(cursor
						.getColumnIndex("uploadtimes")));
				// file.setStatus(UploadFileStatus.UnDeal.getFileStatus(cursor
				// .getInt(cursor.getColumnIndex("status"))));
				file.setStatus(UploadFileStatus.UnDeal);
				file.setType(FileType.Default.getFileStatus(cursor
						.getInt(cursor.getColumnIndex("filetype"))));

				file.setDataBeginTime(sdf.parse(cursor.getString(cursor
						.getColumnIndex("data_begintime"))));
				file.setDataEndTime(sdf.parse(cursor.getString(cursor
						.getColumnIndex("data_endtime"))));
				file.setUploadDate(sdf.parse(cursor.getString(cursor
						.getColumnIndex("upload_date"))));

				files.add(file);
			}
			cursor.close();
			db.close();
			return files;
		} catch (ParseException e) {
			if (ConstantConfig.Debug) {
				LogUtil.e(TAG, e);
			}
		}
		return null;
	}
}
