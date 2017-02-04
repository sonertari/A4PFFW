/*
 * Copyright (C) 2017 Soner Tari
 *
 * This file is part of PFFW.
 *
 * PFFW is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PFFW is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PFFW.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.comixwall.pffw;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import static org.comixwall.pffw.MainActivity.cache;

public class GraphsMbufs extends GraphsBase {

    private Bitmap bmpMbufs;
    private ImageView ivMbufs;

    @Override
    public void init(LayoutInflater inflater, ViewGroup container) {
        view = inflater.inflate(R.layout.graphs_mbufs, container, false);

        mLayout = "mbufs";

        ivMbufs = (ImageView) view.findViewById(R.id.imageViewMbufs);
        ivMbufs.setOnClickListener(onViewClick);

        if (cache.graphsMbufs == null) {
            cache.graphsMbufs = new GraphsCache();
        }
        mModuleCache = cache.graphsMbufs;
    }

    @Override
    public void saveImages() {
        mModuleCache.bmp = bmpMbufs;
    }

    @Override
    public void restoreImages() {
        bmpMbufs = mModuleCache.bmp;
    }

    @Override
    public void setBitmap(String title, Bitmap bmp) {
        if (title.compareTo("Mbuf Statistics") == 0) {
            bmpMbufs = bmp;
        }
    }

    @Override
    public Bitmap getBitmap(View view) {
        return bmpMbufs;
    }

    @Override
    public void updateImages() {
        ivMbufs.setImageBitmap(bmpMbufs);
    }
}

