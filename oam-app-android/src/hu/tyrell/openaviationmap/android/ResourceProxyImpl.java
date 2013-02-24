/*
    Open Aviation Map
    Copyright (C) 2012-2013 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package hu.tyrell.openaviationmap.android;

import org.osmdroid.DefaultResourceProxyImpl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

public class ResourceProxyImpl extends DefaultResourceProxyImpl {

	private final Context mContext;

	public ResourceProxyImpl(final Context pContext) {
		super(pContext);
		mContext = pContext;
	}

	@Override
	public String getString(final string pResId) {
		try {
			final int res = R.string.class.getDeclaredField(pResId.name())
			                                                     .getInt(null);
			return mContext.getString(res);
		} catch (final Exception e) {
			return super.getString(pResId);
		}
	}

	@Override
	public Bitmap getBitmap(final bitmap pResId) {
		try {
			final int res = R.drawable.class.getDeclaredField(pResId.name())
			                                                    .getInt(null);
			return BitmapFactory.decodeResource(mContext.getResources(), res);
		} catch (final Exception e) {
			return super.getBitmap(pResId);
		}
	}

	@Override
	public Drawable getDrawable(final bitmap pResId) {
		try {
			final int res = R.drawable.class.getDeclaredField(pResId.name())
			                                                    .getInt(null);
			return mContext.getResources().getDrawable(res);
		} catch (final Exception e) {
			return super.getDrawable(pResId);
		}
	}
}
