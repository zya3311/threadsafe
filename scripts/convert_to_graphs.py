import json
import os
import re

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def get_line_numbers_from_log(log_file):
    line_numbers = {}  # {variable: {'core': {'class': class, 'method': method, 'line': line}, 'non_core': {...}}}
    current_variable = None
    current_type = None
    
    with open(log_file, 'r') as f:
        for line in f:
            if 'Invalid read:' in line:
                match = re.search(r'variable (.*?) that', line)
                if match:
                    current_variable = match.group(1)
                    line_numbers[current_variable] = {
                        'core': {'class': '', 'method': '', 'line': -1},
                        'non_core': {'class': '', 'method': '', 'line': -1}
                    }
            elif 'Current thread stack trace:' in line:
                current_type = 'core'
            elif 'Previous NON_CORE thread write stack trace:' in line:
                current_type = 'non_core'
            elif line.strip().startswith('at ') and current_variable and current_type:
                # 跳过AccessMonitor.checkAccess的堆栈
                if 'AccessMonitor.checkAccess' in line:
                    continue
                # 匹配完整的类名、方法名和行号
                match = re.match(r'\s*at ([\w\.]+)\.([\w<>]+)\((.*?):(\d+)\)', line)
                if match and line_numbers[current_variable][current_type]['line'] == -1:
                    line_numbers[current_variable][current_type] = {
                        'class': match.group(1),
                        'method': match.group(2),
                        'line': int(match.group(4))
                    }
    
    return line_numbers

def convert_to_simple_json(json_data, line_numbers, output_file):
    with open(output_file, 'w') as f:
        simple_data = []
        for violation in json_data['violations']:
            var = violation['variable']
            info = line_numbers.get(var, {})
            
            # 处理特殊方法名
            def format_method(method):
                if method == '<clinit>':
                    return 'clinit'  # 移除尖括号
                if method == '<init>':
                    return 'init'
                return method
            
            simple_violation = {
                'variable': var,
                'core_thread': {
                    'thread_name': violation['core_thread'],
                    'location': f"{info['core']['class']}.{format_method(info['core']['method'])}:line {info['core']['line']}"
                    if info.get('core', {}).get('line', -1) != -1 else None
                },
                'non_core_thread': {
                    'thread_name': violation['non_core_thread'],
                    'location': f"{info['non_core']['class']}.{format_method(info['non_core']['method'])}:line {info['non_core']['line']}"
                    if info.get('non_core', {}).get('line', -1) != -1 else None
                }
            }
            
            simple_data.append(simple_violation)
        
        json.dump(simple_data, f, indent=2)

if __name__ == '__main__':
    input_file = os.path.join(ROOT_DIR, 'thread_violations.json')
    log_file = os.path.join(ROOT_DIR, 'logs', 'thread-monitor.log')
    output_dir = os.path.join(ROOT_DIR, 'output')
    os.makedirs(output_dir, exist_ok=True)
    
    with open(input_file, 'r') as f:
        data = json.load(f)
    
    line_numbers = get_line_numbers_from_log(log_file)
    output_file = os.path.join(output_dir, 'violations_simple.json')
    convert_to_simple_json(data, line_numbers, output_file)
    
    print(f"File generated: {output_file}") 