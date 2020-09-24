package com.example.httpsender.kt

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * User: ljx
 * Date: 2020/9/24
 * Time: 15:33
 */

fun Uri.dimQuery(context: Context, displayName: String) {
    context.contentResolver.query(this, null,
        "_display_name LIKE '%$displayName%'",null, null)?.use {
        while (it.moveToNext()) {
            val name = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
            val data = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))
            Log.e("LJX", "name=$name data=$data")
        }
    }
}