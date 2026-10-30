"""Aggregate real emulator evidence; original screenshots and old MD3 evidence stay intact."""
import argparse, hashlib, json, shutil
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument('--runs', nargs='+')
    parser.add_argument('--run-prefix', default='refresh')
    parser.add_argument('--output-dir', default='docs/ui-refresh')
    parser.add_argument('--skip-boards', action='store_true')
    args = parser.parse_args()
    prefix = args.run_prefix
    names = args.runs or [f'{prefix}-{s}' for s in ('light','dark','large','small','landscape','tablet','tablet-landscape')] + [f'scroll-{prefix}-large',f'scroll-{prefix}-landscape']
    base = root / 'build/visual-audit'
    apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    runs, versions = [], set()
    for name in names:
        folder = base / name
        report = json.loads((folder / 'report.json').read_text(encoding='utf-8-sig'))
        assert report['errors'] == 0, (name, report['errors'])
        assert report['apkSha256'] == digest, f'APK changed since {name}'
        assert report.get('version'), f'{name}: rerun instrumentation recording BuildConfig.VERSION_NAME'
        versions.add(report['version'])
        assertions = report.get('assertions', [])
        assert all(item['ok'] for item in assertions), (name, assertions)
        screenshots = []
        # Revisited dialogs keep their final screenshot. Count unique files, not repeated captures.
        for screen in {s['name']:s for s in report['screens']}.values():
            assert screen['ok'], (name, screen)
            file = folder / screen['screenshot']
            screenshots.append({'name':screen['name'],'file':file.relative_to(root).as_posix(),'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'smallTargets':screen.get('smallTargets',[]),'edgeClippedTargets':screen.get('edgeClippedTargets',[])})
        runs.append({'run':name,'scope':report.get('scope'),'theme':report['theme'],'fontScale':report['fontScale'],'widthDp':report['widthDp'],'heightDp':report['heightDp'],'androidApi':report['androidApi'],'instrumentationSha256':report.get('instrumentationSha256'),'captureRecords':len(report['screens']),'assertions':assertions,'screenshots':screenshots})
    assert len(versions) == 1, f'Mixed app versions: {versions}'
    output = root / args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    summary = {'version':versions.pop(),'suite':'ui-refresh','apkSha256':digest,'screenshots':sum(len(r['screenshots']) for r in runs),'assertions':sum(len(r['assertions']) for r in runs),'runs':runs}
    (output / 'acceptance-data.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    selected = {
        'light-settings':(f'{prefix}-light','settings'),'dark-settings':(f'{prefix}-dark','settings'),
        'light-playing':(f'{prefix}-light','now_playing'),'dark-playing':(f'{prefix}-dark','now_playing'),
        'large-theme':(f'{prefix}-large','showThemeSettingsDialog'),'large-library':(f'{prefix}-large','library_filters'),
        'tablet-settings':(f'{prefix}-tablet','settings'),'landscape-playing':(f'{prefix}-landscape','now_playing'),
        'large-playing-scrolled':(f'scroll-{prefix}-large','playing_scrolled'),
        'large-rating-five':(f'scroll-{prefix}-large','rating_five_selected'),
        'large-server-bottom':(f'scroll-{prefix}-large','showSubsonicEditDialog_bottom'),
        'landscape-source-scrolled':(f'scroll-{prefix}-landscape','subsonic_search_scrolled'),
    }
    for name in ('mini_unframed','mini_seek_does_not_skip','mini_compact_density','mini_swipe_changes_track','long_title_single_line','long_title_original_tap_gesture','long_title_original_up_gesture','source_dropdown_affordance','player_theme_dark','player_theme_light','dizzylab_long_list','dizzylab_append_keeps_position','dizzylab_load_more_affordance','dizzylab_detail_back_keeps_position','showCacheSettingsDialog','cache_logs_switch_reachable','cache_logs_actions_reachable','cache_temporary_switch_reachable','cache_temporary_actions_reachable','cache_settings_bottom','showDiagnosticsDialog','showLogRetentionDialog','showLogSelectionDialog','showLogPreviewDialog','refresh_settings'):
        selected[name] = (f'{prefix}-light',name)
    for name in ('cache_logs_switch_reachable','cache_logs_actions_reachable','cache_temporary_switch_reachable','cache_temporary_actions_reachable','cache_settings_bottom'):
        selected['large-'+name] = (f'{prefix}-large',name)
    for name in ('log_selection_export_reachable','log_selection_cancel_reachable','log_selection_preview_reachable'):
        selected['large-'+name] = (f'{prefix}-large-actions',name)
    images = output / 'screenshots'
    images.mkdir(exist_ok=True)
    available = {r['run']:{s['name'] for s in r['screenshots']} for r in runs}
    large_run = next((r['run'] for r in runs if r['scope']=='full' and r['fontScale']>=2), f'{prefix}-large')
    copied = []
    for label,(run,screen) in selected.items():
        if run == f'{prefix}-large': run = large_run
        if screen in available.get(run,set()):
            shutil.copy2(base / run / (screen+'.png'), images / (label+'.png'))
            copied.append(label)
    if not args.skip_boards:
        try:
            from PIL import Image, ImageDraw
            for run in runs:
                folder = base / run['run']
                for start in range(0,len(run['screenshots']),6):
                    board = Image.new('RGB',(960,1360),'#e6e6e6')
                    for index,screen in enumerate(run['screenshots'][start:start+6]):
                        with Image.open(root / screen['file']) as original:
                            thumb = original.copy(); thumb.thumbnail((320,640))
                        x,y = index % 3 * 320,index // 3 * 680
                        board.paste(thumb,(x,y+30))
                        ImageDraw.Draw(board).text((x+6,y+8),screen['name'],fill='black')
                    board.save(folder / ('board-%02d.png' % (start//6)))
        except ImportError:
            print('Pillow is optional; raw screenshots and JSON are ready.')
    print(json.dumps({'version':summary['version'],'apkSha256':digest,'runs':len(runs),'screenshots':summary['screenshots'],'assertions':summary['assertions'],'output':output.relative_to(root).as_posix(),'selected':copied,'targetWarnings':{r['run']:sum(bool(s['smallTargets']) for s in r['screenshots']) for r in runs}},ensure_ascii=False))


if __name__ == '__main__':
    main()
