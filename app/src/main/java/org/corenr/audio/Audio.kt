package org.corenr.audio

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.Socket

class Audio : IXposedHookLoadPackage {
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    var soc: Socket = Socket()
    lateinit var activity: Activity
    var loader: ClassLoader? = null
    private val REQUEST_CODE_PERMISSION_AUDIO = 1
    private val REQUEST_CODE_START_CAPTURE = 2
    private fun createAudioPlaybackCaptureConfig(mediaProjection: MediaProjection): AudioPlaybackCaptureConfiguration? {
        val confBuilder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
        confBuilder.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        confBuilder.addMatchingUsage(AudioAttributes.USAGE_GAME)
        confBuilder.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        return confBuilder.build()
    }

    private fun createAudioFormat(): AudioFormat? {
        val builder = AudioFormat.Builder()
        builder.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        builder.setSampleRate(16000)
        builder.setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        return builder.build()
    }

    private fun createAudioRecord(mediaProjection: MediaProjection): AudioRecord? {
        val builder = AudioRecord.Builder()
        builder.setAudioFormat(createAudioFormat()!!)
        builder.setBufferSizeInBytes(6400)
        builder.setAudioPlaybackCaptureConfig(createAudioPlaybackCaptureConfig(mediaProjection)!!)
        return builder.build()
    }

    private val EXTRA_MEDIA_PROJECTION_DATA = "mediaProjectionData"
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam?.appInfo?.packageName!!.indexOf("youdao") != -1) {
            XposedHelpers.findAndHookMethod(ContextWrapper::class.java, "getClassLoader", object : XC_MethodHook() {
                private lateinit var mediaProjectionManager: MediaProjectionManager

                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (param!!.result != null && loader == null) {
                        loader = param.result as ClassLoader?

                        XposedHelpers.findAndHookMethod("com.youdao.ydsimultaneous.SimultaneousActivity", loader, "onCreate", Bundle::class.java,
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        activity = (param!!.thisObject as Activity)
                                        mediaProjectionManager =
                                                activity.getSystemService(AppCompatActivity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        val intent = mediaProjectionManager.createScreenCaptureIntent()
                                        activity.startActivityForResult(intent, REQUEST_CODE_START_CAPTURE)
                                        Log.d("启动", "成功")
                                    }
                                })
                        XposedHelpers.findAndHookMethod(Activity::class.java, "onActivityResult", Int::class.java, Int::class.java, Intent::class.java,
                                object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam?) {
                                        if (param!!.thisObject == null)
                                            return
                                        if (param!!.thisObject.javaClass.name != "com.youdao.ydsimultaneous.SimultaneousActivity")
                                            return
                                        val requestCode = param!!.args[0] as Int
                                        val resultCode = param!!.args[1] as Int
                                        Log.d("开始录音", param.args.joinToString("<"))
                                        if (requestCode == REQUEST_CODE_START_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {

                                            val data = param.args[2] as Intent
                                            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
                                            if (mediaProjection != null)
                                                audioRecord = createAudioRecord(mediaProjection!!)
                                            Log.d("开始录音成功", param.args[2].toString())
                                        }
                                    }
                                })

                    }

                }
            })
            //val fs=XposedHelpers.findClass("com.youdao.audio.recorder.AudioCaptor",lpparam.classLoader).declaredFields
            // Log.d("有道au",fs.joinToString(","))
            XposedHelpers.findAndHookMethod(AudioRecord::class.java, "startRecording", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    //  val record=param!!.thisObject as AudioRecord
                    //if (record.audioSessionId!=audioRecord!!.audioSessionId)
                    XposedBridge.invokeOriginalMethod(param!!.method, audioRecord, param!!.args)
                    return null
                }
            })

            XposedBridge.hookAllMethods(AudioRecord::class.java, "stop", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    //val record=param!!.thisObject as AudioRecord
                    //if (record.audioSessionId!=audioRecord!!.audioSessionId)
                    XposedBridge.invokeOriginalMethod(param!!.method, audioRecord, param!!.args)
                    return null
                }
            })
            XposedBridge.hookAllMethods(AudioRecord::class.java, "release", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    //val record=param!!.thisObject as AudioRecord
                    //if (record.audioSessionId!=audioRecord!!.audioSessionId)
                    XposedBridge.invokeOriginalMethod(param!!.method, audioRecord, param!!.args)
                    return null
                }
            })
            XposedBridge.hookAllMethods(AudioRecord::class.java, "read", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    Log.d("有道读取", param?.args!!.joinToString(","))
                    //val record=param!!.thisObject as AudioRecord
                    //if (record.audioSessionId!=audioRecord!!.audioSessionId)
                    return XposedBridge.invokeOriginalMethod(param!!.method, audioRecord, param!!.args)
                    return null
                }

            })
        }
    }

}