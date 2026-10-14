import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:chewie/chewie.dart';
import 'package:share_plus/share_plus.dart';
import 'package:get/get.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';

class VideoPlayerPage extends StatefulWidget {
  final File videoFile;

  const VideoPlayerPage({super.key, required this.videoFile});

  @override
  State<VideoPlayerPage> createState() => _VideoPlayerPageState();
}

class _VideoPlayerPageState extends State<VideoPlayerPage> {
  late VideoPlayerController videoController;
  ChewieController? chewieController;
  bool isLoading = true;
  double _currentSpeed = 1.0;

  @override
  void initState() {
    super.initState();

    videoController = VideoPlayerController.file(widget.videoFile);

    videoController.initialize().then((_) {
      chewieController = ChewieController(
        videoPlayerController: videoController,
        autoPlay: true,
        looping: false,
        allowFullScreen: true,
        allowMuting: true,
        showControls: true,
        showOptions: false, // Prevents duplicate hidden controls under custom top bar
        materialProgressColors: ChewieProgressColors(
          playedColor: Colors.orange,
          handleColor: Colors.orange,
          bufferedColor: Colors.grey,
          backgroundColor: Colors.grey.shade700,
        ),
      );

      setState(() {
        isLoading = false;
      });
    }).catchError((e) {
      debugPrint("Video Error: $e");
    });
  }

  @override
  void dispose() {
    videoController.dispose();
    chewieController?.dispose();
    super.dispose();
  }

  void _showSpeedDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          'playback_speed'.tr,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [0.5, 0.75, 1.0, 1.25, 1.5, 2.0].map((speed) {
            final isSelected = _currentSpeed == speed;
            return ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                "${speed}x",
                style: TextStyle(
                  color: isSelected ? const Color(0xFFFF6A2B) : Colors.white,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                ),
              ),
              trailing: isSelected
                  ? const Icon(Icons.check_circle, color: Color(0xFFFF6A2B))
                  : null,
              onTap: () {
                setState(() {
                  _currentSpeed = speed;
                });
                videoController.setPlaybackSpeed(speed);
                Navigator.pop(ctx);
              },
            );
          }).toList(),
        ),
      ),
    );
  }

  void _showDetailsDialog() {
    final sizeInBytes =
        widget.videoFile.existsSync() ? widget.videoFile.lengthSync() : 0;
    final sizeInMb = (sizeInBytes / (1024 * 1024)).toStringAsFixed(2);
    final duration = videoController.value.duration;
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    final durationStr = "$minutes min $seconds sec";
    final resolution =
        "${videoController.value.size.width.toInt()} x ${videoController.value.size.height.toInt()}";

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          'video_details'.tr,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildDetailRow('file_name'.tr, widget.videoFile.path.split('/').last),
            const SizedBox(height: 10),
            _buildDetailRow('size'.tr, "$sizeInMb MB"),
            const SizedBox(height: 10),
            _buildDetailRow('duration'.tr, durationStr),
            const SizedBox(height: 10),
            _buildDetailRow('resolution'.tr, resolution),
            const SizedBox(height: 10),
            _buildDetailRow('path'.tr, widget.videoFile.path),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('ok'.tr, style: const TextStyle(color: Color(0xFFFF6A2B))),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: Colors.grey, fontSize: 12),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: const TextStyle(color: Colors.white, fontSize: 14),
        ),
      ],
    );
  }

  void _showDeleteDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          'delete_video'.tr,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: Text(
          'delete_confirm'.tr,
          style: const TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('cancel'.tr, style: const TextStyle(color: Colors.grey)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              try {
                if (widget.videoFile.existsSync()) {
                  widget.videoFile.deleteSync();
                }
              } catch (e) {
                debugPrint("Delete error: $e");
              }
              _onBack();
            },
            child: Text(
              'delete'.tr,
              style: const TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  void _onBack() {
    try {
      if (videoController.value.isPlaying) {
        videoController.pause();
      }
    } catch (_) {}
    AdsVariable.onShowAds(
      context,
      place: 'VD_Player_Back',
      onComplete: () {
        if (mounted) {
          Navigator.pop(context);
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final fileName = widget.videoFile.path.split('/').last;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        _onBack();
      },
      child: Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            /// VIDEO PLAYER
            Center(
              child: isLoading
                  ? const CircularProgressIndicator(color: Colors.white)
                  : Chewie(controller: chewieController!),
            ),

            /// TOP BAR
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      Colors.black.withValues(alpha: 0.75),
                      Colors.transparent,
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                  ),
                ),
                child: Row(
                  children: [
                    /// BACK BUTTON
                    GestureDetector(
                      onTap: _onBack,
                      child: Image.asset(
                        "assets/icons/back_white.png",
                        width: 30,
                        height: 30,
                      ),
                    ),

                    const SizedBox(width: 12),

                    /// VIDEO TITLE
                    Expanded(
                      child: Text(
                        fileName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),

                    /// THREE DOTS MENU
                    PopupMenuButton<String>(
                      icon: const Icon(
                        Icons.more_vert,
                        color: Colors.white,
                        size: 26,
                      ),
                      color: const Color(0xFF242424),
                      elevation: 8,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      onSelected: (value) {
                        if (value == "speed") {
                          _showSpeedDialog();
                        } else if (value == "share") {
                          Share.shareXFiles([XFile(widget.videoFile.path)]);
                        } else if (value == "details") {
                          _showDetailsDialog();
                        } else if (value == "delete") {
                          _showDeleteDialog();
                        }
                      },
                      itemBuilder: (context) => [
                        const PopupMenuItem(
                          value: "speed",
                          child: Row(
                            children: [
                              Icon(Icons.speed, color: Colors.white, size: 20),
                              SizedBox(width: 12),
                              Text("Playback Speed",
                                  style: TextStyle(color: Colors.white)),
                            ],
                          ),
                        ),
                        const PopupMenuItem(
                          value: "share",
                          child: Row(
                            children: [
                              Icon(Icons.share_outlined,
                                  color: Colors.white, size: 20),
                              SizedBox(width: 12),
                              Text("Share",
                                  style: TextStyle(color: Colors.white)),
                            ],
                          ),
                        ),
                        const PopupMenuItem(
                          value: "details",
                          child: Row(
                            children: [
                              Icon(Icons.info_outline,
                                  color: Colors.white, size: 20),
                              SizedBox(width: 12),
                              Text("Details",
                                  style: TextStyle(color: Colors.white)),
                            ],
                          ),
                        ),
                        const PopupMenuItem(
                          value: "delete",
                          child: Row(
                            children: [
                              Icon(Icons.delete_outline,
                                  color: Colors.redAccent, size: 20),
                              SizedBox(width: 12),
                              Text("Delete",
                                  style: TextStyle(color: Colors.redAccent)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
  }
}