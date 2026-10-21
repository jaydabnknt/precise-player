package app.preciseplayer.mpv

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/** The surface mpv renders into. */
class MpvSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = MpvCore.attachSurface(holder.surface)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
        MpvCore.setSurfaceSize(width, height)

    override fun surfaceDestroyed(holder: SurfaceHolder) = MpvCore.detachSurfaceBlocking()
}
