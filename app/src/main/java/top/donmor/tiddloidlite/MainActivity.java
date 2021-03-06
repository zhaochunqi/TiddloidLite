/*
 * top.donmor.tiddloidlite.MainActivity <= [P|Tiddloid Lite]
 * Last modified: 18:18:25 2019/05/10
 * Copyright (c) 2019 donmor
 */

package top.donmor.tiddloidlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
	private RecyclerView rvWikiList;
	private TextView noWiki;
	private WikiListAdapter wikiListAdapter;
	private JSONObject db;

	// CONSTANT
	static final int TAKE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
	static final String
			KEY_NAME = "name",
			KEY_LBL = " — ",
			KEY_FAVICON = "favicon",
			KEY_ID = "id",
			DB_KEY_WIKI = "wiki",
			DB_KEY_URI = "uri",
			KEY_SHORTCUT = "shortcut",
			DB_KEY_SUBTITLE = "subtitle",
			STR_EMPTY = "";
	private static final String
			DB_FILE_NAME = "data.json",
			KEY_APPLICATION_NAME = "application-name",
			KEY_CONTENT = "content",
			KEY_STORE_AREA = "storeArea",
			KEY_PRE = "pre",
			KEY_WIKI_TITLE = "$:/SiteTitle",
			KEY_WIKI_SUBTITLE = "$:/SiteSubTitle",
			KEY_WIKI_FAVICON = "$:/favicon.ico",
			KEY_TITLE = "title",
			TYPE_HTML = "text/html";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFormat(PixelFormat.RGBA_8888);
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
		setContentView(R.layout.activity_main);
		try {
			db = readJson(this);
			if (db == null) throw new Exception();
		} catch (Exception e) {
			e.printStackTrace();
			db = new JSONObject();
			try {
				db.put(DB_KEY_WIKI, new JSONArray());
				writeJson(this, db);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}

		onConfigurationChanged(getResources().getConfiguration());
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		noWiki = findViewById(R.id.t_noWiki);
		try {
			if (db.getJSONArray(DB_KEY_WIKI).length() == 0)
				noWiki.setVisibility(View.VISIBLE);
			else
				noWiki.setVisibility(View.GONE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		rvWikiList = findViewById(R.id.rvWikiList);
		rvWikiList.setLayoutManager(new LinearLayoutManager(this));
		wikiListAdapter = new WikiListAdapter(this, db);
		wikiListAdapter.setReloadListener(new WikiListAdapter.ReloadListener() {
			@Override
			public void onReloaded(int count) {
				if (count > 0) noWiki.setVisibility(View.GONE);
				else noWiki.setVisibility(View.VISIBLE);
			}
		});
		wikiListAdapter.setOnItemClickListener(new WikiListAdapter.ItemClickListener() {
			@Override
			public void onItemClick(final int position, String id, final Uri uri) {
				try {
					if (uri != null && new TWInfo(MainActivity.this, uri).isWiki) {
						getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
						if (!loadPage(id))
							Toast.makeText(MainActivity.this, R.string.error_loading_page, Toast.LENGTH_SHORT).show();
					} else {
						new AlertDialog.Builder(MainActivity.this)
								.setTitle(android.R.string.dialog_alert_title)
								.setMessage(R.string.confirm_to_auto_remove_wiki)
								.setNegativeButton(android.R.string.no, null)
								.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										try {
											db.getJSONArray(DB_KEY_WIKI).remove(position);
											writeJson(MainActivity.this, db);
											if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
												revokeUriPermission(getPackageName(), uri, TAKE_FLAGS);
										} catch (Exception e) {
											e.printStackTrace();
										}
										MainActivity.this.onResume();
									}
								}).show();
					}
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_SHORT).show();
				}
			}

			@Override
			public void onItemLongClick(final int position, final String id, final Uri uri, final String name, final String sub, Bitmap favicon) {
				try {
					Drawable icon = getDrawable(R.drawable.ic_description);
					try {
						if (favicon != null)
							icon = new BitmapDrawable(getResources(), favicon);
					} catch (Exception e) {
						e.printStackTrace();
					}
					final IconCompat iconCompat = favicon != null ? IconCompat.createWithBitmap(favicon) : IconCompat.createWithResource(MainActivity.this, R.drawable.ic_shortcut);
					TextView view = new TextView(MainActivity.this);
					DocumentFile file = DocumentFile.fromSingleUri(MainActivity.this, uri);
					String fn = file != null ? file.getName() : null;
					CharSequence s = getString(R.string.provider)
							+ uri.getAuthority()
							+ '\n'
							+ getString(R.string.pathDir)
							+ Uri.decode(uri.getLastPathSegment())
							+ '\n'
							+ getString(R.string.filename)
							+ (fn != null && fn.length() > 0 ? fn : getString(R.string.unknown));
					view.setText(s);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
						view.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
					AlertDialog wikiConfigDialog = new AlertDialog.Builder(MainActivity.this)
							.setTitle(name)
							.setIcon(icon)
							.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									MainActivity.this.onResume();
								}
							})
							.setNegativeButton(R.string.remove_wiki, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									dialogInterface.dismiss();
									AlertDialog removeWikiConfirmationDialog = new AlertDialog.Builder(MainActivity.this)
											.setTitle(android.R.string.dialog_alert_title)
											.setMessage(R.string.confirm_to_remove_wiki)
											.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialogInterface, int i) {
													MainActivity.this.onResume();
												}
											})
											.setNeutralButton(R.string.delete_the_html_file, new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialogInterface, int i) {
													try {
														db.getJSONArray(DB_KEY_WIKI).remove(position);
														writeJson(MainActivity.this, db);
														DocumentsContract.deleteDocument(getContentResolver(), uri);
														if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
															revokeUriPermission(getPackageName(), uri, TAKE_FLAGS);
														Toast.makeText(MainActivity.this, R.string.file_deleted, Toast.LENGTH_SHORT).show();
													} catch (Exception e) {
														e.printStackTrace();
														Toast.makeText(MainActivity.this, R.string.failed, Toast.LENGTH_SHORT).show();
													}
													MainActivity.this.onResume();
												}
											})
											.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													try {
														db.getJSONArray(DB_KEY_WIKI).remove(position);
														writeJson(MainActivity.this, db);
														if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
															revokeUriPermission(getPackageName(), uri, TAKE_FLAGS);
													} catch (Exception e) {
														e.printStackTrace();
														Toast.makeText(MainActivity.this, R.string.failed, Toast.LENGTH_SHORT).show();
													}
													MainActivity.this.onResume();
												}
											})
											.create();
									removeWikiConfirmationDialog.show();
								}
							})
							.setNeutralButton(R.string.create_shortcut, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									try {
										Bundle bu = new Bundle();
										bu.putString(KEY_ID, id);
										bu.putBoolean(KEY_SHORTCUT, true);
										Intent in = new Intent(MainActivity.this, TWEditorWV.class).putExtras(bu).setAction(Intent.ACTION_MAIN);
										if (ShortcutManagerCompat.isRequestPinShortcutSupported(MainActivity.this)) {
											ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(MainActivity.this, id)
													.setShortLabel(name)
													.setLongLabel(name + (sub != null ? (KEY_LBL + sub) : MainActivity.STR_EMPTY))
													.setIcon(iconCompat)
													.setIntent(in)
													.build();
											if (ShortcutManagerCompat.requestPinShortcut(MainActivity.this, shortcut, null))
												Toast.makeText(MainActivity.this, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
											else throw new Exception();
										}
									} catch (Exception e) {
										e.printStackTrace();
										Toast.makeText(MainActivity.this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show();
									}
								}
							})
							.create();
					int m = Math.round(20 * getResources().getDisplayMetrics().density + 0.5f);
					wikiConfigDialog.setView(view, m, 0, m, 0);
					wikiConfigDialog.show();
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	private Boolean loadPage(String id) {
		Intent in = new Intent();
		try {
			Bundle bu = new Bundle();
			String vid = null;
			for (int i = 0; i < db.getJSONArray(DB_KEY_WIKI).length(); i++) {
				if (db.getJSONArray(DB_KEY_WIKI).getJSONObject(i).getString(KEY_ID).equals(id)) {
					vid = id;
					break;
				}
			}
			if (vid != null) {
				bu.putString(KEY_ID, vid);
				in.putExtras(bu)
						.setClass(MainActivity.this, TWEditorWV.class);
				startActivity(in);
			} else throw new Exception();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_new) {
			startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_HTML), 43);
		} else if (id == R.id.action_import) {
			startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_HTML), 42);
		} else if (id == R.id.action_about) {
			SpannableString spannableString = new SpannableString(getString(R.string.about));
			Linkify.addLinks(spannableString, Linkify.ALL);
			AlertDialog aboutDialog = new AlertDialog.Builder(this)
					.setTitle(R.string.action_about)
					.setMessage(spannableString)
					.show();
			((TextView) aboutDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
				((TextView) aboutDialog.findViewById(android.R.id.message)).setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Widget_TextView);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		super.onActivityResult(requestCode, resultCode, resultData);
		if (resultCode == Activity.RESULT_OK && resultData != null) {
			final Uri uri = resultData.getData();
			if (uri != null)
				switch (requestCode) {
					case 43:
						final ProgressDialog progressDialog = new ProgressDialog(this);
						progressDialog.setMessage(getString(R.string.please_wait));
						progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
						progressDialog.setCanceledOnTouchOutside(false);
						final Thread thread = new Thread(new Runnable() {
							@Override
							public void run() {
								InputStream is = null;
								OutputStream os = null;
								boolean interrupted = false;
								boolean iNet = false;
								try {
									os = getContentResolver().openOutputStream(uri);
									if (os != null) {
										URL url = new URL(getString(R.string.template_repo));
										HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
										httpsURLConnection.connect();
										iNet = true;
										is = httpsURLConnection.getInputStream();
										int length;
										byte[] bytes = new byte[4096];
										while ((length = is.read(bytes)) > -1) {
											os.write(bytes, 0, length);
											if (Thread.currentThread().isInterrupted()) {
												interrupted = true;
												break;
											}
										}
										os.flush();
										os.close();
										os = null;
										if (!interrupted) {
											TWInfo info = new TWInfo(MainActivity.this, uri);
											if (!info.isWiki)
												throw new Exception();
											progressDialog.dismiss();
											String id = genId();
											try {
												boolean exist = false;
												JSONObject w = null;
												for (int i = 0; i < db.getJSONArray(DB_KEY_WIKI).length(); i++) {
													w = db.getJSONArray(DB_KEY_WIKI).getJSONObject(i);
													if (w.getString(DB_KEY_URI).equals(uri.toString())) {
														exist = true;
														id = w.getString(KEY_ID);
														break;
													}
												}
												if (exist) {
													runOnUiThread(new Runnable() {
														@Override
														public void run() {
															Toast.makeText(MainActivity.this, R.string.wiki_replaced, Toast.LENGTH_SHORT).show();
														}
													});
													new File(getDir(MainActivity.KEY_FAVICON, MODE_PRIVATE), id).delete();
												} else {
													w = new JSONObject();
													w.put(KEY_ID, id);
													db.getJSONArray(DB_KEY_WIKI).put(db.getJSONArray(DB_KEY_WIKI).length(), w);
												}
												w.put(KEY_NAME, (info.title != null && info.title.length() > 0) ? info.title : getString(R.string.tiddlywiki));
												w.put(DB_KEY_SUBTITLE, (info.subtitle != null && info.subtitle.length() > 0) ? info.subtitle : STR_EMPTY);
												w.put(DB_KEY_URI, uri.toString());
												updateIcon(MainActivity.this, info.favicon, id);
												if (!MainActivity.writeJson(MainActivity.this, db))
													throw new Exception();
												getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
											} catch (Exception e) {
												e.printStackTrace();
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_SHORT).show();
													}
												});
											}
											runOnUiThread(new Runnable() {
												@Override
												public void run() {
													MainActivity.this.onResume();
												}
											});
											if (!loadPage(id))
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														Toast.makeText(MainActivity.this, R.string.error_loading_page, Toast.LENGTH_SHORT).show();
													}
												});
										}
									}
								} catch (Exception e) {
									e.printStackTrace();
									progressDialog.dismiss();
									try {
										DocumentsContract.deleteDocument(getContentResolver(), uri);
									} catch (Exception e1) {
										e.printStackTrace();
									}
									final int fid = iNet ? R.string.failed_creating_file : R.string.no_internet;
									runOnUiThread(new Runnable() {
										@Override
										public void run() {
											Toast.makeText(MainActivity.this, fid, Toast.LENGTH_SHORT).show();
										}
									});
								} finally {
									if (is != null)
										try {
											is.close();
										} catch (Exception e) {
											e.printStackTrace();
										}
									if (os != null)
										try {
											os.close();
										} catch (Exception e) {
											e.printStackTrace();
										}
									if (interrupted) try {
										DocumentsContract.deleteDocument(getContentResolver(), uri);
										runOnUiThread(new Runnable() {
											@Override
											public void run() {
												Toast.makeText(MainActivity.this, R.string.cancelled, Toast.LENGTH_SHORT).show();
											}
										});
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
							}
						});

						progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getText(android.R.string.cancel), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								progressDialog.cancel();
							}
						});
						progressDialog.setOnShowListener(new DialogInterface.OnShowListener() {
							@Override
							public void onShow(DialogInterface dialog) {
								thread.start();
							}
						});
						progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface dialogInterface) {
								thread.interrupt();
							}
						});
						progressDialog.show();
						break;
					case 42:
						String id = genId();
						TWInfo info = new TWInfo(MainActivity.this, uri);
						if (info.isWiki) {
							try {
								boolean exist = false;
								for (int i = 0; i < db.getJSONArray(DB_KEY_WIKI).length(); i++) {
									if (db.getJSONArray(DB_KEY_WIKI).getJSONObject(i).getString(DB_KEY_URI).equals(uri.toString())) {
										exist = true;
										id = db.getJSONArray(DB_KEY_WIKI).getJSONObject(i).getString(KEY_ID);
										break;
									}
								}
								if (exist) {
									Toast.makeText(MainActivity.this, R.string.wiki_already_exists, Toast.LENGTH_SHORT).show();
								} else {
									JSONObject w = new JSONObject();
									w.put(KEY_NAME, (info.title != null && info.title.length() > 0) ? info.title : getString(R.string.tiddlywiki));
									w.put(DB_KEY_SUBTITLE, (info.subtitle != null && info.subtitle.length() > 0) ? info.subtitle : STR_EMPTY);
									w.put(KEY_ID, id);
									w.put(DB_KEY_URI, uri.toString());
									db.getJSONArray(DB_KEY_WIKI).put(db.getJSONArray(DB_KEY_WIKI).length(), w);
									updateIcon(this, info.favicon, id);
								}
								if (!MainActivity.writeJson(MainActivity.this, db))
									throw new Exception();
								getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
							} catch (Exception e) {
								e.printStackTrace();
								Toast.makeText(MainActivity.this, R.string.data_error, Toast.LENGTH_SHORT).show();
							}
							MainActivity.this.onResume();
							if (!loadPage(id))
								Toast.makeText(MainActivity.this, R.string.error_loading_page, Toast.LENGTH_SHORT).show();
						} else {
							Toast.makeText(MainActivity.this, R.string.not_a_wiki, Toast.LENGTH_SHORT).show();
						}
						break;
				}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		try {
			db = readJson(this);
			wikiListAdapter.reload(db);
			rvWikiList.setAdapter(wikiListAdapter);
			if (db.getJSONArray(DB_KEY_WIKI).length() == 0)
				noWiki.setVisibility(View.VISIBLE);
			else
				noWiki.setVisibility(View.GONE);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) try {
			getWindow().setStatusBarColor(getColor(R.color.design_default_color_primary));
			getWindow().getDecorView().setSystemUiVisibility((newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : View.SYSTEM_UI_FLAG_VISIBLE);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	static JSONObject readJson(Context context) {
		byte[] b;
		InputStream is = null;
		JSONObject jsonObject = null;
		try {
			is = context.openFileInput(DB_FILE_NAME);
			b = new byte[is.available()];
			if (is.read(b) < 0) throw new Exception();
			jsonObject = new JSONObject(new String(b));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		return jsonObject;
	}

	static boolean writeJson(Context context, JSONObject vdb) {
		boolean v;
		OutputStream os = null;
		try {
			os = context.openFileOutput(DB_FILE_NAME, MODE_PRIVATE);
			byte[] b = vdb.toString(2).getBytes();
			os.write(b);
			os.flush();
			v = true;
		} catch (Exception e) {
			e.printStackTrace();
			v = false;
		} finally {
			if (os != null)
				try {
					os.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		return v;
	}

	static void updateIcon(Context context, Bitmap icon, String id) {
		File fi = new File(context.getDir(MainActivity.KEY_FAVICON, MODE_PRIVATE), id);
		if (icon != null) {
			OutputStream os = null;
			try {
				os = new FileOutputStream(fi);
				icon.compress(Bitmap.CompressFormat.PNG, 100, os);
				os.flush();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (os != null)
					try {
						os.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
			}
		} else fi.delete();
	}

	private static String genId() {
		return UUID.randomUUID().toString();
	}

	static class TWInfo {
		boolean isWiki;
		String title = null, subtitle = null;
		Bitmap favicon = null;

		TWInfo(Context context, Uri uri) {
			InputStream is = null;
			try {
				is = new BufferedInputStream(Objects.requireNonNull(context.getContentResolver().openInputStream(uri)));
				byte[] bytes = new byte[is.available()];
				is.read(bytes);
				Document doc = Jsoup.parse(new String(bytes, 0, bytes.length, StandardCharsets.UTF_8), uri.toString());
				Element an = doc.getElementsByAttributeValue(KEY_NAME, KEY_APPLICATION_NAME).first();
				isWiki = an != null && an.attr(KEY_CONTENT).equals(context.getString(R.string.tiddlywiki));
				if (isWiki) {
					Element ti = doc.getElementsByTag(KEY_TITLE).first();
					title = ti != null ? ti.html() : title;
					Element sa = doc.getElementsByAttributeValue(KEY_ID, KEY_STORE_AREA).first();
					Element t1 = sa.getElementsByAttributeValue(KEY_TITLE, KEY_WIKI_TITLE).first();
					Element t2 = sa.getElementsByAttributeValue(KEY_TITLE, KEY_WIKI_SUBTITLE).first();
					title = t1 != null ? t1.getElementsByTag(KEY_PRE).first().html() : title;
					subtitle = t2 != null ? t2.getElementsByTag(KEY_PRE).first().html() : subtitle;
					Element fi = sa.getElementsByAttributeValue(KEY_TITLE, KEY_WIKI_FAVICON).first();
					byte[] b = fi != null ? Base64.decode(fi.getElementsByTag(KEY_PRE).first().html(), Base64.NO_PADDING) : new byte[0];
					favicon = BitmapFactory.decodeByteArray(b, 0, b.length);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (is != null) try {
					is.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}