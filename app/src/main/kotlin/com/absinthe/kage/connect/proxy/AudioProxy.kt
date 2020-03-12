package com.absinthe.kage.connect.proxy

import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.absinthe.kage.connect.protocol.IpMessageConst
import com.absinthe.kage.connect.protocol.IpMessageProtocol
import com.absinthe.kage.device.Device
import com.absinthe.kage.device.Device.OnReceiveMsgListener
import com.absinthe.kage.device.cmd.*
import com.absinthe.kage.device.model.AudioInfo
import com.google.gson.Gson
import timber.log.Timber

object AudioProxy : BaseProxy() {

    private var mInquiryCurrentPositionThread: InquiryCurrentPositionThread? = null
    private var mInquiryPlayStateThread: InquiryPlayStateThread? = null
    private var mInquiryDurationThread: InquiryDurationThread? = null
    private var mOnPlayListener: OnPlayListener? = null

    private const val PLAY_POSITION_INQUIRY_PERIOD = 1000
    private val mOnReceiveMsgListener: OnReceiveMsgListener
    private val mPlayInfo: PlayInfo = PlayInfo()
    private val mHandler = Handler(Looper.getMainLooper())

    init {
        mOnReceiveMsgListener = object : OnReceiveMsgListener {
            override fun onReceiveMsg(msg: String) {
                parserMsgAndNotifyIfNeed(msg)
            }
        }
    }

    fun play(audioInfo: AudioInfo?) {
        if (mDevice != null && mDevice!!.isConnected
                && audioInfo != null && audioInfo.url != null) {
            mDevice!!.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
            cancelInquiryPlayState()
            cancelInquiryCurrentPosition()
            cancelInquiryDuration()
            resetCurrentPlayInfo()
            mDevice!!.registerOnReceiveMsgListener(mOnReceiveMsgListener)

            val stopCmd = StopCommand()
            mDevice!!.sendCommand(stopCmd)

            val preparePlayCmd = MediaPreparePlayCommand()
            preparePlayCmd.type = MediaPreparePlayCommand.TYPE_MUSIC
            mDevice!!.sendCommand(preparePlayCmd)

            val audioInfoCommand = AudioInfoCommand()
            audioInfoCommand.url = audioInfo.url
            audioInfoCommand.name = audioInfo.name
            audioInfoCommand.artist = audioInfo.artist
            audioInfoCommand.album = audioInfo.album
            audioInfoCommand.coverPath = audioInfo.coverPath
            mDevice!!.sendCommand(audioInfoCommand)

            mPlayInfo.isPlayListMode = false
            scheduleInquiryPlayState(1000)
        }
    }

    fun start() {
        if (mPlayInfo.playState == PLAY_STATUS.PAUSED_PLAYBACK.status && null != mDevice && mDevice!!.isConnected) {
            val resumePlayCommand = ResumePlayCommand()
            mDevice!!.sendCommand(resumePlayCommand)
        }
    }

    fun pause() {
        if (mPlayInfo.playState == PLAY_STATUS.PLAYING.status && mDevice != null && mDevice!!.isConnected) {
            val pauseCmd = MediaPausePlayingCommand()
            mDevice!!.sendCommand(pauseCmd)
        }
    }

    fun stop() {
        if (null != mDevice && mDevice!!.isConnected) {
            val stopCmd = StopCommand()
            mDevice!!.sendCommand(stopCmd)
        }
    }

    fun seekTo(position: Int) {
        if (mPlayInfo.playState != PLAY_STATUS.STOPPED.status && null != mDevice && mDevice!!.isConnected) {
            val seekToCmd = SeekToCommand()
            seekToCmd.position = position
            mDevice!!.sendCommand(seekToCmd)
            mPlayInfo.position = position
        }
    }

    @Synchronized
    fun setOnPlayListener(onPlayListener: OnPlayListener?) {
        mOnPlayListener = onPlayListener
    }

    val playState: Int
        get() {
            return mPlayInfo.playState
        }

    val duration: Int
        get() = mPlayInfo.duration

    val currentPosition: Int
        get() = mPlayInfo.position

    fun recycle() {
        mDevice?.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
        cancelInquiryCurrentPosition()
        cancelInquiryDuration()
        cancelInquiryPlayState()
    }

    fun playPrevious() {
        if (null != mDevice && mDevice!!.isConnected) {
            if (!mPlayInfo.isPlayListMode) {
                return
            }
            mDevice!!.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
            cancelInquiryPlayState()
            cancelInquiryCurrentPosition()
            cancelInquiryDuration()
            resetCurrentPlayInfo()
            mDevice!!.registerOnReceiveMsgListener(mOnReceiveMsgListener)
            val playPreCmd = PlayPreviousCommand()
            mDevice!!.sendCommand(playPreCmd)
            scheduleInquiryPlayState(1000)
        }
    }

    fun playNext() {
        if (null != mDevice && mDevice!!.isConnected) {
            if (!mPlayInfo.isPlayListMode) {
                return
            }
            mDevice!!.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
            cancelInquiryPlayState()
            cancelInquiryCurrentPosition()
            cancelInquiryDuration()
            resetCurrentPlayInfo()
            mDevice!!.registerOnReceiveMsgListener(mOnReceiveMsgListener)
            val playNextCmd = PlayNextCommand()
            mDevice!!.sendCommand(playNextCmd)
            scheduleInquiryPlayState(1000)
        }
    }

    fun playList(index: Int, list: List<AudioInfo?>?) {
        if (null != mDevice && mDevice!!.isConnected && null != list && list.isNotEmpty()) {
            mDevice!!.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
            cancelInquiryPlayState()
            cancelInquiryCurrentPosition()
            cancelInquiryDuration()
            resetCurrentPlayInfo()
            mDevice!!.registerOnReceiveMsgListener(mOnReceiveMsgListener)

            val stopCmd = StopCommand()
            mDevice!!.sendCommand(stopCmd)

            val preparePlayCmd = MediaPreparePlayCommand()
            preparePlayCmd.type = MediaPreparePlayCommand.TYPE_MUSIC
            mDevice!!.sendCommand(preparePlayCmd)

            val playListCmd = PlayAudioListCommand()
            playListCmd.index = index
            playListCmd.size = list.size
            playListCmd.listInfo = Gson().toJson(list)
            mDevice!!.sendCommand(playListCmd)

            mPlayInfo.isPlayListMode = true
            scheduleInquiryPlayState(1000)
        }
    }

    fun setPlayAudioMode(mode: Int) {
        if (null != mDevice && mDevice!!.isConnected) {
            val setAudioModeCmd = SetAudioModeCommand()
            setAudioModeCmd.mode = mode
            mDevice!!.sendCommand(setAudioModeCmd)
        }
    }

    fun setPlayIndex(index: Int) {
        if (null != mDevice && mDevice!!.isConnected) {
            if (!mPlayInfo.isPlayListMode) {
                return
            }
            if (index < 0) {
                return
            }
            val setPlayIndexCommand = SetPlayIndexCommand()
            setPlayIndexCommand.index = index
            mDevice!!.sendCommand(setPlayIndexCommand)
            resetCurrentPlayInfo()
        }
    }

    private fun parserMsgAndNotifyIfNeed(msg: String?) {
        if (msg != null) {
            Timber.d("msg = $msg")

            val split = msg.split(IpMessageProtocol.DELIMITER).toTypedArray()
            if (split.size < 2) {
                return
            }
            try {
                when (split[0].toInt()) {
                    IpMessageConst.RESPONSE_SET_PLAYBACK_PROGRESS -> {
                        val position = split[1].toInt()
                        if (mPlayInfo.duration <= 0) {
                            return
                        }
                        mPlayInfo.position = position
                        notifyOnCurrentPositionChanged(mPlayInfo)
                    }
                    IpMessageConst.RESPONSE_SET_MEDIA_DURATION -> {
                        val duration = split[1].toInt()
                        if (duration <= 0) {
                            return
                        }
                        mPlayInfo.duration = duration
                        cancelInquiryDuration()
                        notifyOnCurrentPositionChanged(mPlayInfo)
                        scheduleInquiryCurrentPosition() //获取到总长度后询问当前播放进度
                    }
                    IpMessageConst.MEDIA_SET_PLAYER_STATUS -> {
                        val playerState = split[1].toInt()
                        val playOldState = mPlayInfo.playState
                        if (PlayStatue.PLAYER_EXIT == playerState) {
                            onPlayStopped()
                        }
                        notifyOnPlayStateChanged(playOldState, playerState)
                    }
                    IpMessageConst.MEDIA_SET_PLAYING_STATE -> {
                        val newState = split[1].toInt()
                        val oldState = mPlayInfo.playState
                        if (oldState == newState) {
                            return
                        }
                        if (PlayStatue.PLAYING == newState) {
                            scheduleInquiryDuration() //定时询问长度，直到获取到合法长度停止询问。
                        } else {
                            cancelInquiryCurrentPosition()
                            cancelInquiryDuration()
                        }
                        if (PlayStatue.STOPPED == newState) {
                            onPlayStopped()
                        }
                        mPlayInfo.playState = newState
                        notifyOnPlayStateChanged(oldState, newState)
                    }
                    IpMessageConst.RESPONSE_PLAYING_INDEX -> {
                        resetCurrentPlayInfo()
                        val index = split[1].toInt()
                        notifyOnPlayIndexChanged(index)
                    }
                }
            } catch (e: Exception) {
                Timber.e("protocol invalid: ${e.message}")
            }
        }
    }

    private fun onPlayStopped() {
        resetCurrentPlayInfo()
        cancelInquiryPlayState()
    }

    private fun notifyOnCurrentPositionChanged(playInfo: PlayInfo) {
        mHandler.post {
            if (mOnPlayListener != null) {
                Timber.d("notifyOnCurrentPositionChanged: duration = ${playInfo.duration}, position = ${playInfo.position}")
                mOnPlayListener!!.onCurrentPositionChanged(playInfo.duration, playInfo.duration)
            }
        }
    }

    private fun notifyOnPlayStateChanged(oldState: Int, newState: Int) {
        mHandler.post {
            if (mOnPlayListener != null) {
                mOnPlayListener!!.onPlayStateChanged(oldState, newState)
            }
        }
    }

    private fun notifyOnPlayIndexChanged(index: Int) {
        mHandler.post {
            if (mOnPlayListener != null) {
                mOnPlayListener!!.onPlayIndexChanged(index)
            }
        }
    }

    private fun resetCurrentPlayInfo() {
        mPlayInfo.duration = 0
        mPlayInfo.position = 0
        mPlayInfo.playState = PlayStatue.STOPPED
    }

    private fun scheduleInquiryPlayState(period: Int) {
        cancelInquiryPlayState()
        mInquiryPlayStateThread = InquiryPlayStateThread(mDevice)
        mInquiryPlayStateThread!!.setPeriod(period)
        mInquiryPlayStateThread!!.start()
    }

    private fun cancelInquiryPlayState() {
        if (mInquiryPlayStateThread != null) {
            mInquiryPlayStateThread!!.interrupt()
            mInquiryPlayStateThread = null
        }
    }

    private fun scheduleInquiryDuration() {
        cancelInquiryDuration()
        mInquiryDurationThread = InquiryDurationThread(mDevice, mPlayInfo)
        mInquiryDurationThread!!.start()
    }

    private fun cancelInquiryDuration() {
        if (null != mInquiryDurationThread) {
            mInquiryDurationThread!!.interrupt()
            mInquiryDurationThread = null
        }
    }

    private class InquiryDurationThread(private val mDevice: Device?, private val mPlayInfo: PlayInfo?) : Thread() {
        private val inquiryDurationCmd = InquiryDurationCommand()
        override fun run() {
            while (true) {
                if (isInterrupted || mPlayInfo == null) {
                    break
                }
                if (mDevice == null || !mDevice.isConnected) {
                    return
                }
                mDevice.sendCommand(inquiryDurationCmd)
                try {
                    sleep(1000)
                } catch (e: InterruptedException) {
                    Timber.e("InquiryDurationThread is interrupted")
                    break
                }
            }
        }

    }

    private fun inquiryDuration() {
        val inquiryDurationCmd = InquiryDurationCommand()
        mDevice!!.sendCommand(inquiryDurationCmd)
    }

    private fun scheduleInquiryCurrentPosition() {
        cancelInquiryCurrentPosition()
        mInquiryCurrentPositionThread = InquiryCurrentPositionThread(
                mDevice, PLAY_POSITION_INQUIRY_PERIOD, PLAY_POSITION_INQUIRY_PERIOD, mPlayInfo)
        mInquiryCurrentPositionThread!!.start()
    }

    private fun cancelInquiryCurrentPosition() {
        if (mInquiryCurrentPositionThread != null) {
            mInquiryCurrentPositionThread!!.interrupt()
            mInquiryCurrentPositionThread = null
        }
    }

    private class InquiryCurrentPositionThread internal constructor(
            private val mDevice: Device?,
            private val mUpdatePeriod: Int,
            private val mInquiryPeriod: Int,
            private val mPlayInfo: PlayInfo?) : Thread() {

        private var mNoInquiryMills: Int
        private val positionCommand = InquiryPlayingPositionCommand()
        override fun run() {
            while (true) {
                if (isInterrupted || mPlayInfo == null) {
                    break
                }
                if (mDevice == null || !mDevice.isConnected) {
                    return
                }
                if (mNoInquiryMills >= mInquiryPeriod) {
                    mDevice.sendCommand(positionCommand)
                    mNoInquiryMills = 0
                } else {
                    //simulate update
                    if (mPlayInfo.position + mUpdatePeriod >= mPlayInfo.duration) {
                        mPlayInfo.position = mPlayInfo.duration
                    } else {
                        mPlayInfo.position += mUpdatePeriod
                    }
                    notifyOnCurrentPositionChanged(mPlayInfo)
                }
                mNoInquiryMills += try {
                    sleep(mUpdatePeriod.toLong())
                    mUpdatePeriod
                } catch (e: InterruptedException) {
                    Timber.e("InquiryCurrentPositionThread is interrupted")
                    break
                }
            }
        }

        init {
            mNoInquiryMills = mInquiryPeriod
        }
    }

    private class InquiryPlayStateThread internal constructor(private val mDevice: Device?) : Thread() {
        private var period = 2000
        override fun run() {
            while (true) {
                if (isInterrupted || mDevice == null || !mDevice.isConnected) {
                    break
                }
                val inquiryPlayStateCmd = InquiryPlayStateCommand()
                mDevice.sendCommand(inquiryPlayStateCmd)
                val inquiryPlayStatusCmd = InquiryPlayerStatusCommand()
                mDevice.sendCommand(inquiryPlayStatusCmd)
                val inquiryPeriod = period.toLong()
                try {
                    sleep(inquiryPeriod)
                } catch (e: InterruptedException) {
                    Timber.e("InquiryCurrentPositionThread is interrupted")
                    break
                }
            }
        }

        fun setPeriod(period: Int) {
            this.period = period
        }

    }

    private class PlayInfo {
        var duration = 0
        var position = 0
        var playState = 0
        var isPlayListMode = false
    }

    //  播放器状态
    private enum class PLAYER_STATUS(private val status: Int) {
        OK(PlayStatue.OK),
        PLAYER_EXIT(PlayStatue.PLAYER_EXIT),
        ERROR_OCCURRED(PlayStatue.ERROR_OCCURRED);
    }

    //  播放状态
    private enum class PLAY_STATUS(val status: Int) {
        STOPPED(PlayStatue.STOPPED),
        PLAYING(PlayStatue.PLAYING),
        PAUSED_PLAYBACK(PlayStatue.PAUSED),
        TRANSITIONING(PlayStatue.TRANSITIONING);
    }

    override fun onDeviceConnected(device: Device) {
        if (mDevice != device) {
            if (mDevice != null) {
                mDevice!!.unregisterOnReceiveMsgListener(mOnReceiveMsgListener)
                cancelInquiryPlayState()
                cancelInquiryCurrentPosition()
                cancelInquiryDuration()
            }
            mDevice = device
        }
    }

    override fun onDeviceDisconnected(device: Device) {
        super.onDeviceDisconnected(device)
        val playerState = PlayStatue.DISCONNECT
        val playOldState = mPlayInfo.playState
        onPlayStopped()
        notifyOnPlayStateChanged(playOldState, playerState)
    }

    interface PlayStatue {
        companion object {
            const val INVALIDATE = -1
            const val STOPPED = PlaybackState.STATE_STOPPED
            const val TRANSITIONING = PlaybackState.STATE_BUFFERING
            const val PLAYING = PlaybackState.STATE_PLAYING
            const val PAUSED = PlaybackState.STATE_PAUSED
            const val OK = 10
            const val PLAYER_EXIT = 11
            const val ERROR_OCCURRED = 12
            const val DISCONNECT = 20
        }
    }

    interface OnPlayListener {
        fun onCurrentPositionChanged(duration: Int, position: Int)
        fun onPlayStateChanged(oldState: Int, newState: Int)
        fun onPlayIndexChanged(index: Int)
    }
}