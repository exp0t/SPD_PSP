"""Builds psp/dist/SPD: EBOOT.PBP plus the game assets laid out for the PSP.
Images bigger than 512x512 pixels are stored downscaled; assets/scale.txt tells the loader the original size
factor so Pixmap keeps reporting the original dimensions (SPD uses normalized UVs, so drawing is unaffected)."""
import os, shutil, sys, wave
import miniaudio
from PIL import Image

PSP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(PSP)
SRC = os.path.join(ROOT, 'core', 'src', 'main', 'assets')
DIST = os.path.join(PSP, 'dist', 'SPD')
DST = os.path.join(DIST, 'assets')


# message overrides for the PSP controls: file -> {key: value} (values in .properties syntax)
TEXT = {
    'messages/scenes/scenes.properties': {
        'scenes.welcomescene.controller_body': r'The D-pad or the analog stick moves your hero and picks menu items.\n\nCross selects, Circle goes back. In the dungeon Cross opens a tile cursor (targeted items and wands start it on the nearest enemy), Circle opens the inventory and Start the game menu. Hold Select for more actions.',
        'scenes.gamescene.tutorial_move_controller': r'Use the D-pad or the analog stick to move, or press Cross to pick a tile with the cursor.',
    },
    'messages/scenes/scenes_ru.properties': {
        'scenes.welcomescene.controller_body': r'Крестовина или аналоговый стик перемещают героя и выбирают пункты меню.\n\nКрестик выбирает, кружок возвращает назад. В подземелье крестик открывает курсор клеток (для броска и жезлов он сразу встаёт на ближайшего врага), кружок открывает инвентарь, а Start меню игры. Удерживайте Select для дополнительных действий.',
        'scenes.gamescene.tutorial_move_controller': r'Двигайтесь крестовиной или аналоговым стиком, либо нажмите крестик, чтобы выбрать клетку курсором.',
    },
    'messages/windows/windows.properties': {
        'windows.wndkeybindings.controller_info': r'The D-pad or the analog stick moves your character\nCross opens a cursor to pick any tile, Circle the inventory\nHold Select for the second row of buttons',
    },
    'messages/windows/windows_ru.properties': {
        'windows.wndkeybindings.controller_info': r'Крестовина или аналоговый стик перемещают персонажа\nКрестик открывает курсор для выбора клетки, кружок инвентарь\nУдерживайте Select для второго ряда кнопок',
    },
}


# keys the PSP port adds (the "fixed camera" option in the display settings), in every language
NEW_TEXT = {
    'messages/windows/windows.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Fixed camera'},
    'messages/windows/windows_be.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Фіксаваная камера'},
    'messages/windows/windows_cs.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Pevná kamera'},
    'messages/windows/windows_de.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Feste Kamera'},
    'messages/windows/windows_el.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Σταθερή κάμερα'},
    'messages/windows/windows_eo.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Fiksita kamerao'},
    'messages/windows/windows_es.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Cámara fija'},
    'messages/windows/windows_fr.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Caméra fixe'},
    'messages/windows/windows_hu.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Rögzített kamera'},
    'messages/windows/windows_in.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Kamera tetap'},
    'messages/windows/windows_it.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Telecamera fissa'},
    'messages/windows/windows_ja.properties': {'windows.wndsettings$displaytab.fixed_camera': 'カメラ固定'},
    'messages/windows/windows_ko.properties': {'windows.wndsettings$displaytab.fixed_camera': '카메라 고정'},
    'messages/windows/windows_nl.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Vaste camera'},
    'messages/windows/windows_pl.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Stała kamera'},
    'messages/windows/windows_pt.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Câmera fixa'},
    'messages/windows/windows_ru.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Фиксированная камера'},
    'messages/windows/windows_sv.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Fast kamera'},
    'messages/windows/windows_tr.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Sabit kamera'},
    'messages/windows/windows_uk.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Фіксована камера'},
    'messages/windows/windows_vi.properties': {'windows.wndsettings$displaytab.fixed_camera': 'Camera cố định'},
    'messages/windows/windows_zh-hant.properties': {'windows.wndsettings$displaytab.fixed_camera': '固定鏡頭'},
    'messages/windows/windows_zh.properties': {'windows.wndsettings$displaytab.fixed_camera': '固定镜头'},
}


def patch_properties(src, dst, values, added):
    lines = open(src, encoding='utf-8').read().split('\n')
    left = dict(values)
    for i, line in enumerate(lines):
        key = line.split('=', 1)[0]
        if key in added:
            sys.exit('assets: %s already has %s' % (src, key))
        if key in left:
            lines[i] = key + '=' + left.pop(key)
    if left:
        sys.exit('assets: keys not found in %s: %s' % (src, list(left)))
    lines += [k + '=' + v for k, v in added.items()]
    open(dst, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')


def main():
    if os.path.isdir(DST):
        shutil.rmtree(DST)
    scales = []
    for d, _, names in os.walk(SRC):
        for n in names:
            src = os.path.join(d, n)
            rel = os.path.relpath(src, SRC).replace(os.sep, '/')
            dst = os.path.join(DST, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if n.endswith('.png'):
                img = Image.open(src)
                w, h = img.size
                f = 1
                while w * h // (f * f) > 512 * 512:
                    f *= 2
                if f > 1:
                    img = img.convert('RGBA').resize((w // f, h // f), Image.LANCZOS)
                    img.save(dst, optimize=True)
                    scales.append('%s %d' % (rel, f))
                    continue
            if n.endswith('.jpg'):
                # 800x450 splash art: the GU tops out at 512 px, store it at half size
                img = Image.open(src)
                w, h = img.size
                if w > 512 or h > 512:
                    img.convert('RGB').resize((w // 2, h // 2), Image.LANCZOS).save(dst, quality=90)
                    scales.append('%s %d' % (rel, 2))
                    continue
            if n.endswith('.ogg') and rel.startswith('music/'):
                # the PSP decodes music in hardware from SPD's MP3 set (made for iOS, kept in psp/assets/music);
                # the game still asks for .ogg
                shutil.copyfile(os.path.join(PSP, 'assets', rel[:-4] + '.mp3'), dst[:-4] + '.mp3')
                continue
            if rel in TEXT or rel in NEW_TEXT:
                patch_properties(src, dst, TEXT.get(rel, {}), NEW_TEXT.get(rel, {}))
                continue
            if n.endswith('.mp3') and rel.startswith('sounds/'):
                # sound effects are decoded once to 22 kHz mono PCM; the PSP mixer plays WAV from RAM
                pcm = miniaudio.decode_file(src, output_format=miniaudio.SampleFormat.SIGNED16, nchannels=1, sample_rate=22050)
                with wave.open(dst[:-4] + '.wav', 'wb') as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(22050)
                    w.writeframes(pcm.samples.tobytes())
                continue
            shutil.copyfile(src, dst)
    with open(os.path.join(DST, 'scale.txt'), 'w', newline='\n') as f:
        f.write('\n'.join(scales) + '\n')
    eboot = os.path.join(PSP, 'native', 'EBOOT.PBP')
    if os.path.exists(eboot):
        shutil.copyfile(eboot, os.path.join(DIST, 'EBOOT.PBP'))
    print('assets ->', DIST, '; downscaled:', scales)


if __name__ == '__main__':
    main()
